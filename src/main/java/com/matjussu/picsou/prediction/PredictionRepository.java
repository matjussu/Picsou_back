package com.matjussu.picsou.prediction;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {

  Optional<Prediction> findByUserIdAndForecastDate(UUID userId, LocalDate forecastDate);
}
