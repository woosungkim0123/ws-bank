package shop.woosung.bank.account.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import shop.woosung.bank.account.infrastructure.entity.AccountEntity;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {

    List<AccountEntity> findByUserId(Long userId);

    Optional<AccountEntity> findByFullnumber(Long fullnumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.fullnumber = :fullnumber")
    Optional<AccountEntity> findByFullnumberWithPessimisticLock(Long fullnumber);
}
