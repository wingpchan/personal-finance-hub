package com.financehub.user_service.repository;

import com.financehub.user_service.entity.User;
import com.financehub.user_service.entity.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, UserId> {

    @Query(value = "SELECT nextval('user_sequence')", nativeQuery = true)
    Long getNextUserId();

    // Get current active record by id
    @Query("SELECT u FROM User u WHERE u.userId.id = :id " +
            "AND u.endDate IS NULL")
    Optional<User> findCurrentById(@Param("id") Long id);

    // Get full version history for a user
    @Query("SELECT u FROM User u WHERE u.userId.id = :id " +
            "ORDER BY u.userId.effectiveDate DESC")
    List<User> findAllVersionsById(@Param("id") Long id);

    // Get current record by email
    @Query("SELECT u FROM User u WHERE u.email = :email " +
            "AND u.endDate IS NULL")
    Optional<User> findCurrentByEmail(@Param("email") String email);

    // Check if email exists on any current record
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email " +
            "AND u.endDate IS NULL")
    boolean existsByEmail(@Param("email") String email);

    // Get all current active users
    @Query("SELECT u FROM User u WHERE u.endDate IS NULL")
    List<User> findAllCurrentUsers();

    // Search by first name on current records
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) " +
            "LIKE LOWER(CONCAT('%', :firstName, '%')) " +
            "AND u.endDate IS NULL")
    List<User> findCurrentByFirstNameContaining(
            @Param("firstName") String firstName);

    // Search by last name on current records
    @Query("SELECT u FROM User u WHERE LOWER(u.lastName) " +
            "LIKE LOWER(CONCAT('%', :lastName, '%')) " +
            "AND u.endDate IS NULL")
    List<User> findCurrentByLastNameContaining(
            @Param("lastName") String lastName);

    // Search across first and last name on current records
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND u.endDate IS NULL")
    List<User> findCurrentByName(@Param("name") String name);

    // Search by both first and last name on current records
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :firstName, '%')) " +
            "AND LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%')) " +
            "AND u.endDate IS NULL")
    List<User> findCurrentByFirstNameAndLastNameContaining(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);

    // Point in time query - find user record at a specific date
    @Query("SELECT u FROM User u WHERE u.userId.id = :id " +
            "AND u.userId.effectiveDate <= :queryDate " +
            "AND (u.endDate IS NULL OR u.endDate < :queryDate) " +
            "ORDER BY u.userId.effectiveDate DESC")
    List<User> findByIdAtPointInTime(
            @Param("id") Long id,
            @Param("queryDate") LocalDateTime queryDate);

    // Get ALL users including historical
    @Query("SELECT u FROM User u WHERE u.endDate IS NOT NULL")
    List<User> findAllHistoricalUsers();

    // Get latest record per user regardless of active status
    @Query("SELECT u FROM User u WHERE u.userId.effectiveDate = " +
            "(SELECT MAX(u2.userId.effectiveDate) FROM User u2 " +
            "WHERE u2.userId.id = u.userId.id)")
    List<User> findLatestVersionAllUsers();

    // Search by first name - latest version only
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) " +
            "LIKE LOWER(CONCAT('%', :firstName, '%')) " +
            "AND u.userId.effectiveDate = " +
            "(SELECT MAX(u2.userId.effectiveDate) FROM User u2 " +
            "WHERE u2.userId.id = u.userId.id)")
    List<User> findLatestVersionByFirstNameContaining(
            @Param("firstName") String firstName);

    // Search by last name - latest version only
    @Query("SELECT u FROM User u WHERE LOWER(u.lastName) " +
            "LIKE LOWER(CONCAT('%', :lastName, '%')) " +
            "AND u.userId.effectiveDate = " +
            "(SELECT MAX(u2.userId.effectiveDate) FROM User u2 " +
            "WHERE u2.userId.id = u.userId.id)")
    List<User> findLatestVersionByLastNameContaining(
            @Param("lastName") String lastName);

    // Search across first and last name - latest version only
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
            "AND u.userId.effectiveDate = " +
            "(SELECT MAX(u2.userId.effectiveDate) FROM User u2 " +
            "WHERE u2.userId.id = u.userId.id)")
    List<User> findLatestVersionByName(@Param("name") String name);

    // Search by both first and last name - latest version only
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) " +
            "LIKE LOWER(CONCAT('%', :firstName, '%')) " +
            "AND LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%')) " +
            "AND u.userId.effectiveDate = " +
            "(SELECT MAX(u2.userId.effectiveDate) FROM User u2 " +
            "WHERE u2.userId.id = u.userId.id)")
    List<User> findLatestVersionByFirstNameAndLastNameContaining(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);

    // Search by first name including historical
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) " +
            "LIKE LOWER(CONCAT('%', :firstName, '%'))")
    List<User> findAllByFirstNameContaining(
            @Param("firstName") String firstName);

    // Search by last name including historical
    @Query("SELECT u FROM User u WHERE LOWER(u.lastName) " +
            "LIKE LOWER(CONCAT('%', :lastName, '%'))")
    List<User> findAllByLastNameContaining(
            @Param("lastName") String lastName);

    // Search across first and last name including historical
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :name, '%')))")
    List<User> findAllByName(@Param("name") String name);

    // Search by both first and last name including historical
    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) " +
            "LIKE LOWER(CONCAT('%', :firstName, '%')) " +
            "AND LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))")
    List<User> findAllByFirstNameAndLastNameContaining(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName);
}