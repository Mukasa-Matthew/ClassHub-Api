package com.classhub.announcement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("""
            select a from Announcement a
            join fetch a.createdBy
            join fetch a.academicClass
            where a.id = :id
            """)
    Optional<Announcement> findDetailedById(@Param("id") UUID id);

    @Query("""
            select a from Announcement a
            join fetch a.createdBy
            join fetch a.academicClass
            where (:status is null or a.status = :status)
            order by a.createdAt desc
            """)
    List<Announcement> findAllDetailed(@Param("status") AnnouncementStatus status);

    @Query("""
            select a from Announcement a
            join fetch a.createdBy
            join fetch a.academicClass
            where a.academicClass.id = :classId
              and (:status is null or a.status = :status)
            order by a.createdAt desc
            """)
    List<Announcement> findAllDetailedByClass(
            @Param("classId") UUID classId, @Param("status") AnnouncementStatus status);

    long countByStatus(AnnouncementStatus status);

    @Query("""
            select a from Announcement a
            join fetch a.academicClass
            where a.status = :status
            order by a.publishedAt desc
            """)
    List<Announcement> findTop5ByStatusOrderByPublishedAtDesc(@Param("status") AnnouncementStatus status);
}
