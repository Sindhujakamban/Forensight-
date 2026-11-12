package com.forensight.backend.repository;

import com.forensight.backend.model.Evidence;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EvidenceRepository extends MongoRepository<Evidence, String> {

    List<Evidence> findByCaseId(String caseId);
}