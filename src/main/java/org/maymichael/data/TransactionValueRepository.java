package org.maymichael.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionValueRepository extends CrudRepository<TransactionValue, String> {
}
