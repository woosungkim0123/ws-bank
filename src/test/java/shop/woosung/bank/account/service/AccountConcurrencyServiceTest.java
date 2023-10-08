package shop.woosung.bank.account.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import shop.woosung.bank.account.controller.port.AccountService;
import shop.woosung.bank.account.domain.Account;
import shop.woosung.bank.account.domain.AccountType;
import shop.woosung.bank.account.service.dto.*;
import shop.woosung.bank.account.service.port.AccountRepository;
import shop.woosung.bank.common.infrastructure.PasswordEncoderImpl;
import shop.woosung.bank.transaction.domain.TransactionType;
import shop.woosung.bank.user.domain.User;
import shop.woosung.bank.user.domain.UserRole;
import shop.woosung.bank.user.service.port.UserRepository;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class AccountConcurrencyServiceTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @MockBean
    private PasswordEncoderImpl passwordEncoderImpl;

    @BeforeEach
    void setUp() {
        when(passwordEncoderImpl.encode(anyString())).thenReturn("aaaa-bbbb-cccc");
        when(passwordEncoderImpl.matches(anyString(), anyString())).thenReturn(true);
    }

    @DisplayName("계좌 생성 요청을 동시에 100개를 보냈을 때 계좌번호가 중복되는 계좌가 없어야 한다.")
    @Test
    void account_register_100_concurrent_requests_not_have_duplicate_accounts() throws Exception {
        int threadCount = 100;
        User user = userRepository.save(User.builder().email("testcon@test.com").password("1234").name("test").role(UserRole.CUSTOMER).build());
        ExecutorService executorService = Executors.newFixedThreadPool(25);

        List<Long> accountFullNumbers = Collections.synchronizedList(new ArrayList<>());

        AccountRegisterRequestServiceDto normalAccountRegisterServiceDto = AccountRegisterRequestServiceDto.builder()
                .type(AccountType.NORMAL)
                .password("1111")
                .build();

        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    AccountRegisterResponseDto accountRegisterResponseDto = accountService.register(normalAccountRegisterServiceDto, user);
                    accountFullNumbers.add(accountRegisterResponseDto.getFullNumber());
                } finally{
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        Set<Long> accountFullNemberSet = new HashSet<>(accountFullNumbers);

        assertThat(accountFullNumbers).hasSize(threadCount);
        assertThat(accountFullNemberSet).hasSize(threadCount);
    }

    @DisplayName("계좌 입금 요청을 동시에 100개를 보냈을 때 최종 금액이 모든 요청 금액을 합친 것과 같아야한다.")
    @Test
    void account_deposit_100_concurrent_requests_not_() throws Exception {
        int threadCount = 100;
        User user = userRepository.save(User.builder().email("testcon2@test.com").password("1234").name("test").role(UserRole.CUSTOMER).build());
        accountRepository.save(Account.builder().number(11111111L).fullNumber(23411111111L).password("aaaa-bbbb-cccc").balance(1000L).type(AccountType.NORMAL).user(user).build());
        AtomicLong maxAmount = new AtomicLong(0);

        ExecutorService executorService = Executors.newFixedThreadPool(25);

        AccountDepositRequestServiceDto accountDepositRequestServiceDto = AccountDepositRequestServiceDto.builder()
                .fullNumber(23411111111L)
                .amount(1000L)
                .transactionType(TransactionType.DEPOSIT)
                .sender("32311111111")
                .tel("010-1111-1111")
                .build();

        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    AccountDepositResponseDto accountDepositResponseDto = accountService.deposit(accountDepositRequestServiceDto);
                    long currentBalance = accountDepositResponseDto.getTransaction().getDepositAccountBalance();
                    maxAmount.accumulateAndGet(currentBalance, Math::max);
                } finally{
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        assertThat(maxAmount.get()).isEqualTo(101000L);
    }

    @DisplayName("계좌 출금 요청을 동시에 100개를 보냈을 때 최종 금액이 모든 요청 금액을 뺀 것과 같아야한다.")
    @Test
    void account_withdraw_100_concurrent_requests_not_() throws Exception {
        int threadCount = 100;
        User user = userRepository.save(User.builder().email("testcon3@test.com").password("aaaa-bbbb-cccc").name("test").role(UserRole.CUSTOMER).build());
        accountRepository.save(Account.builder().number(11111111L).fullNumber(23311111112L).password("aaaa-bbbb-cccc").balance(999999L).type(AccountType.NORMAL).user(user).build());
        AtomicLong maxAmount = new AtomicLong(999999L);

        ExecutorService executorService = Executors.newFixedThreadPool(25);

        AccountWithdrawRequestServiceDto accountWithdrawRequestServiceDto = AccountWithdrawRequestServiceDto.builder()
                .fullNumber(23311111112L)
                .amount(100L)
                .transactionType(TransactionType.WITHDRAW)
                .password("aaaa-bbbb-cccc")
                .build();

        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    AccountWithdrawResponseDto accountWithdrawResponseDto = accountService.withdraw(accountWithdrawRequestServiceDto, user);
                    long currentBalance = accountWithdrawResponseDto.getTransaction().getWithdrawAccountBalance();
                    maxAmount.accumulateAndGet(currentBalance, Math::min);
                } catch(Exception e) {
                    System.out.println("e = " + e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        assertThat(maxAmount.get()).isEqualTo(989999L);
    }
}