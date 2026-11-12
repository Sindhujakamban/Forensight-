package com.forensight.backend.repository;

import com.forensight.backend.model.Case;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CaseRepository extends MongoRepository<Case, String> {
}