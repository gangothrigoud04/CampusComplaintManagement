package com.campus.complaints;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusLocationRepository extends JpaRepository<CampusLocation, Long> {
    java.util.Optional<CampusLocation> findFirstByBuildingIgnoreCaseAndRoomIsNull(String building);
}
