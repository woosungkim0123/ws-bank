package shop.woosung.bank.account.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shop.woosung.bank.account.controller.port.AccountLockService;
import shop.woosung.bank.account.controller.port.AccountService;
import shop.woosung.bank.account.domain.Account;
import shop.woosung.bank.account.domain.AccountSequence;
import shop.woosung.bank.account.domain.AccountType;
import shop.woosung.bank.account.domain.AccountTypeNumber;
import shop.woosung.bank.account.handler.exception.NotFoundAccountFullNumberException;
import shop.woosung.bank.account.handler.exception.NotFoundAccountSequenceException;
import shop.woosung.bank.account.handler.exception.NotFoundAccountTypeNumberException;

import shop.woosung.bank.account.handler.exception.SameAccountTransferException;
import shop.woosung.bank.account.service.dto.*;
import shop.woosung.bank.account.service.port.AccountRepository;
import shop.woosung.bank.account.service.port.AccountSequenceRepository;
import shop.woosung.bank.account.service.port.AccountTypeNumberRepository;
import shop.woosung.bank.common.service.port.PasswordEncoder;
import shop.woosung.bank.transaction.domain.Transaction;
import shop.woosung.bank.transaction.domain.TransactionType;
import shop.woosung.bank.transaction.service.port.TransactionRepository;
import shop.woosung.bank.user.domain.User;
import shop.woosung.bank.user.service.port.UserRepository;

import java.util.List;

import static shop.woosung.bank.account.util.AccountServiceToDomainConverter.*;
import static shop.woosung.bank.account.util.AccountServiceToServiceConverter.accountWithdrawLockServiceDtoConvert;

@Builder
@RequiredArgsConstructor
@Service
public class AccountServiceImpl implements AccountService {
    private final AccountLockService accountLockService;
    private final AccountRepository accountRepository;
    private final AccountSequenceRepository accountSequenceRepository;
    private final AccountTypeNumberRepository accountTypeNumberRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AccountRegisterResponseDto register(AccountRegisterRequestServiceDto accountRegisterRequestServiceDto, User user) {
        Long typeNumber = getTypeNumber(accountRegisterRequestServiceDto.getType());
        Long newNumber = getNewNumber(accountRegisterRequestServiceDto.getType());

        Account account = Account.register(accountRegisterConvert(accountRegisterRequestServiceDto, typeNumber, newNumber, user), passwordEncoder);
        Account newAccount = accountRepository.save(account);

        return AccountRegisterResponseDto.from(newAccount);
    }

    @Transactional(readOnly = true)
    public AccountListResponseDto getAccountList(User user) {
        List<Account> userAccounts = accountRepository.findByUserId(user.getId());

        return AccountListResponseDto.from(user, userAccounts);
    }

    @Override
    public void deleteAccount(Long fullNumber, User user) {
        Account account = accountRepository.findByFullNumber(fullNumber)
                .orElseThrow(() -> new NotFoundAccountFullNumberException(fullNumber));

        account.checkOwner(user.getId());

        accountRepository.deleteById(account.getId());
    }

    @Transactional
    public AccountDepositResponseDto deposit(AccountDepositRequestServiceDto accountDepositRequestServiceDto) {
        Account depositAccount = accountLockService.depositAccountWithLock(accountDepositRequestServiceDto.getFullNumber(), accountDepositRequestServiceDto.getAmount());

        Transaction depositTransaction = transactionRepository.save(
                Transaction.createDepositTransaction(depositTransactionCreateConvert(accountDepositRequestServiceDto, depositAccount))
        );

        return AccountDepositResponseDto.from(depositAccount, depositTransaction);
    }

    @Transactional
    public AccountWithdrawResponseDto withdraw(AccountWithdrawRequestServiceDto accountWithdrawRequestServiceDto, User user) {
        Account withdrawAccount = accountLockService.withdrawWithLock(accountWithdrawLockServiceDtoConvert(accountWithdrawRequestServiceDto, user));

        // TODO 추후 트랜잭션 서비스로 이동 고려
        Transaction transaction = Transaction.createWithdrawTransaction(withdrawTransactionCreateConvert(accountWithdrawRequestServiceDto, withdrawAccount, "ATM"));

        Transaction withdrawTransaction = transactionRepository.save(transaction);

        return AccountWithdrawResponseDto.from(withdrawAccount, withdrawTransaction);
    }

    @Transactional
    public AccountTransferResponseDto transfer(AccountTransferRequestServiceDto accountTransferRequestServiceDto, Long userId) {

        checkSameAccount(accountTransferRequestServiceDto.getWithdrawFullNumber(), accountTransferRequestServiceDto.getDepositFullNumber());

        Account withdrawAccount = findAccountByFullNumber(accountTransferRequestServiceDto.getWithdrawFullNumber());

        Account depositAccount = findAccountByFullNumber(accountTransferRequestServiceDto.getDepositFullNumber());

        withdrawAccount.checkOwner(userId);
        withdrawAccount.checkPasswordMatch(String.valueOf(accountTransferRequestServiceDto.getWithdrawPassword()), passwordEncoder);

        withdrawAccount.withdraw(accountTransferRequestServiceDto.getAmount());
        depositAccount.deposit(accountTransferRequestServiceDto.getAmount());

        Transaction transaction = Transaction.builder()
                .withdrawAccount(withdrawAccount)
                .depositAccount(depositAccount)
                .withdrawAccountBalance(withdrawAccount.getBalance())
                .depositAccountBalance(depositAccount.getBalance())
                .amount(accountTransferRequestServiceDto.getAmount())
                .type(TransactionType.TRANSFER)
                .sender(accountTransferRequestServiceDto.getWithdrawFullNumber() + "")
                .receiver(accountTransferRequestServiceDto.getDepositFullNumber() + "")
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        return AccountTransferResponseDto.from(withdrawAccount, savedTransaction);
    }

    private Account findAccountByFullNumber(Long fullNumber) {
        return accountRepository.findByFullNumber(fullNumber)
                .orElseThrow(() -> new NotFoundAccountFullNumberException(fullNumber));
    }

//
//    @Transactional(readOnly = true)
//    public AccountDetailResDto getAccountDetail(Long number, Long userId, Integer page) {
//        String type = "ALL";
//
//        AccountEntity accountEntityPS = accountJpaRepository.findByNumber(number)
//                .orElseThrow(() -> new CustomApiException("계좌를 찾을 수 없습니다."));
//
//        accountEntityPS.checkOwner(userId);
//
//        List<TransactionEntity> transactionList = transactionRepository.findTransactionList(accountEntityPS.getId(), type, page);
//
//        // DTO 응답
//        return new AccountDetailResDto(accountEntityPS, transactionList);
//    }
//    private Long getNewNumber() {
//        return accountRepository.findLastNumberWithPessimisticLock()
//                .map(account -> account.getNumber() + 1L)
//                .orElse(11111111111L);
//    }

    private Long getTypeNumber(AccountType accountType) {
        AccountTypeNumber accountTypeNumber = accountTypeNumberRepository.findById(accountType.name())
                .orElseThrow(() -> new NotFoundAccountTypeNumberException(accountType));
        return accountTypeNumber.getNumber();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long getNewNumber (AccountType accountType) {
        AccountSequence accountSequence = accountSequenceRepository.findById(accountType.name())
                .orElseThrow(() -> new NotFoundAccountSequenceException(accountType));

        Long nextValue = accountSequence.getNextValue();
        accountSequence.incrementNextValue();
        accountSequenceRepository.save(accountSequence);
        return nextValue;
    }

    private void checkSameAccount(Long withdrawFullNumber, Long depositFullNumber) {
        if(withdrawFullNumber.equals(depositFullNumber)) {
            throw new SameAccountTransferException(withdrawFullNumber);
        }
    }
}
