package shop.woosung.bank.account.controller.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.woosung.bank.transaction.domain.TransactionType;

import javax.validation.constraints.*;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class AccountWithdrawRequestDto {

    @NotNull
    @Digits(integer = 19, fraction = 0)
    private Long fullNumber;

    @NotNull
    @Digits(integer = 4, fraction = 4)
    private Long password;

    @Positive
    @NotNull
    private Long amount;

    @NotNull
    private TransactionType transactionType;

    @Builder
    public AccountWithdrawRequestDto(Long fullNumber, Long password, Long amount, TransactionType transactionType) {
        this.fullNumber = fullNumber;
        this.password = password;
        this.amount = amount;
        this.transactionType = transactionType;
    }

}
