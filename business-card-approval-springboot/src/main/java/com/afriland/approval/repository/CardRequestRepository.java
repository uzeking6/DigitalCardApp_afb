package com.afriland.approval.repository;

import com.afriland.approval.model.CardRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface CardRequestRepository extends JpaRepository<CardRequest, Long> {
    List<CardRequest> findByStatusOrderByIdDesc(String status);
    List<CardRequest> findAllByOrderByIdDesc();
    List<CardRequest> findByStatusInOrderByIdDesc(List<String> statuses);
    Optional<CardRequest> findFirstByEmailAndStatusNotOrderByIdDesc(String email, String excludeStatus);
    Optional<CardRequest> findFirstByEmailOrderByIdDesc(String email);
    List<CardRequest> findByEmailIgnoreCaseOrderByIdDesc(String email);

    @Query("SELECT r.status, COUNT(r) FROM CardRequest r GROUP BY r.status")
    List<Object[]> countByStatus();
}
