package org.maymichael.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionValueRepository extends CrudRepository<TransactionValue, String> {

    List<TransactionValue> findAllByTid(String transactionId);
}
