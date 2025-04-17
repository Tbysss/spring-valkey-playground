package org.maymichael.data;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionKeyRepository extends CrudRepository<TransactionKey,String> {
}
