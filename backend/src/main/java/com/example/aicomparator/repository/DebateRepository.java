package com.example.aicomparator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aicomparator.entity.Debate;

public interface DebateRepository extends JpaRepository<Debate, Long> {

    List<Debate> findAllByOrderByUpdatedAtDescIdDesc();
}
