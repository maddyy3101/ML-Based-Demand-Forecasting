package com.powergrid.forecasting.service;

import com.powergrid.forecasting.client.MlApiClient;
import com.powergrid.forecasting.dto.admin.RetrainingStatusDto;
import com.powergrid.forecasting.dto.admin.SystemHealthDto;
import com.powergrid.forecasting.dto.admin.UploadResponseDto;
import com.powergrid.forecasting.dto.admin.UserSummaryDto;
import com.powergrid.forecasting.dto.admin.UserUpdateRequest;
import com.powergrid.forecasting.dto.auth.RegisterRequest;
import com.powergrid.forecasting.entity.PowerGridUser;
import com.powergrid.forecasting.entity.ProcurementForecast;
import com.powergrid.forecasting.entity.RetrainingJob;
import com.powergrid.forecasting.enums.RetrainingStatus;
import com.powergrid.forecasting.exception.ResourceNotFoundException;
import com.powergrid.forecasting.exception.ValidationFailureException;
import com.powergrid.forecasting.repository.PowerGridUserRepository;
import com.powergrid.forecasting.repository.ProcurementForecastRepository;
import com.powergrid.forecasting.repository.RetrainingJobRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminService {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "Project_ID",
            "Project_Phase",
            "State",
            "Region",
            "Terrain_Type",
            "Tower_Type",
            "Substation_Type",
            "Transmission_Length_KM",
            "Budget_Crore",
            "Material_Type",
            "Lead_Time_Days",
            "Tax_Percentage",
            "Transportation_Cost",
            "Historical_Consumption",
            "Month",
            "Year",
            "Quantity_Required"
    );

    private final Path uploadDir;
    private final RetrainingJobRepository retrainingJobRepository;
    private final ForecastService forecastService;
    private final ProcurementForecastRepository forecastRepository;
    private final PowerGridUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final RetrainingExecutorService retrainingExecutorService;
    private final MlApiClient mlApiClient;
    private final DataSource dataSource;

    public AdminService(
            @Value("${file.upload-dir:./uploads}") String uploadDir,
            RetrainingJobRepository retrainingJobRepository,
            ForecastService forecastService,
            ProcurementForecastRepository forecastRepository,
            PowerGridUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            RetrainingExecutorService retrainingExecutorService,
            MlApiClient mlApiClient,
            DataSource dataSource
    ) {
        this.uploadDir = Path.of(uploadDir);
        this.retrainingJobRepository = retrainingJobRepository;
        this.forecastService = forecastService;
        this.forecastRepository = forecastRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.retrainingExecutorService = retrainingExecutorService;
        this.mlApiClient = mlApiClient;
        this.dataSource = dataSource;
    }

    @Transactional
    public UploadResponseDto uploadDataset(MultipartFile file, PowerGridUser triggeredBy) {
        validateFile(file);
        try {
            Files.createDirectories(uploadDir);
            HeaderCheck headerCheck = validateHeaderAndCountRows(file);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Path storedFile = uploadDir.resolve("powergrid_dataset_" + timestamp + ".csv");
            Files.copy(file.getInputStream(), storedFile, StandardCopyOption.REPLACE_EXISTING);

            Path activeFile = uploadDir.resolve("powergrid_material_dataset.csv");
            Files.copy(storedFile, activeFile, StandardCopyOption.REPLACE_EXISTING);

            RetrainingJob job = new RetrainingJob();
            job.setDatasetPath(activeFile.toAbsolutePath().toString());
            job.setStatus(RetrainingStatus.PENDING);
            job.setRowCount(headerCheck.rowCount());
            job.setTriggeredBy(triggeredBy);
            job.setLogOutput("Dataset uploaded by " + triggeredBy.getUsername());

            RetrainingJob saved = retrainingJobRepository.save(job);
            retrainingExecutorService.executeRetraining(saved);

            return new UploadResponseDto(
                    saved.getId().toString(),
                    file.getOriginalFilename(),
                    headerCheck.rowCount(),
                    saved.getStatus().name(),
                    "Dataset accepted and retraining queued"
            );
        } catch (IOException ex) {
            throw new ValidationFailureException("Failed to store dataset: " + ex.getMessage());
        }
    }

    public RetrainingStatusDto getRetrainingStatus(UUID jobId) {
        RetrainingJob job = retrainingJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Retraining job not found: " + jobId));
        return new RetrainingStatusDto(
                job.getId().toString(),
                job.getStatus().name(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getRowCount(),
                job.getLogOutput(),
                job.getModelMetricsJson()
        );
    }

    public List<UserSummaryDto> getUsers() {
        return userRepository.findAll().stream().map(authService::toSummary).toList();
    }

    public UserSummaryDto createUser(RegisterRequest request) {
        return authService.register(request);
    }

    @Transactional
    public UserSummaryDto updateUser(UUID userId, UserUpdateRequest request) {
        PowerGridUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setRole(request.role());
        user.setAssignedRegion(request.assignedRegion());
        user.setActive(request.active());
        return authService.toSummary(userRepository.save(user));
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        PowerGridUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void reactivateUser(UUID userId) {
        PowerGridUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setActive(true);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUserPermanently(UUID userId) {
        PowerGridUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.isActive()) {
            throw new ValidationFailureException("Deactivate user before permanent deletion.");
        }
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ValidationFailureException("Cannot delete user because related audit/inventory records exist.");
        }
    }

    public SystemHealthDto getSystemHealth() {
        String flaskStatus = "down";
        String activeModelType = "Unavailable";
        Double modelRmse = null;
        Double modelMae = null;

        try {
            Map<String, Object> health = mlApiClient.getHealth();
            Map<String, Object> model = mlApiClient.getModelInfo();
            Map<String, Object> metrics = (Map<String, Object>) model.getOrDefault("metrics", Map.of());
            flaskStatus = String.valueOf(health.getOrDefault("status", "down"));
            activeModelType = String.valueOf(model.getOrDefault("model_type", "Unknown"));
            modelRmse = toDouble(metrics.get("RMSE"));
            modelMae = toDouble(metrics.get("MAE"));
        } catch (Exception ignored) {
            flaskStatus = "down";
        }

        Instant lastRetrained = retrainingJobRepository.findTopByOrderByCreatedAtDesc()
                .map(RetrainingJob::getCompletedAt)
                .orElse(null);

        long todayCount = forecastRepository.countByCreatedAtAfter(Instant.now().minusSeconds(86400));
        long total = forecastRepository.count();

        return new SystemHealthDto(
                flaskStatus,
                resolveDbStatus(),
                activeModelType,
                modelRmse,
                modelMae,
                lastRetrained,
                todayCount,
                total
        );
    }

    private String resolveDbStatus() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next() && resultSet.getInt(1) == 1) {
                return "connected";
            }
            return "degraded";
        } catch (Exception ex) {
            return "disconnected";
        }
    }

    public Page<ProcurementForecast> getAuditLog(Pageable pageable) {
        return forecastRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private HeaderCheck validateHeaderAndCountRows(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ValidationFailureException("CSV is empty.");
            }
            List<String> headers = List.of(headerLine.split(","));
            Set<String> missing = new LinkedHashSet<>(REQUIRED_COLUMNS);
            missing.removeAll(headers);
            if (!missing.isEmpty()) {
                throw new ValidationFailureException("Missing required columns: " + String.join(", ", missing));
            }

            int rows = 0;
            while (reader.readLine() != null) {
                rows++;
            }
            return new HeaderCheck(rows);
        }
    }

    private void validateFile(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".csv")) {
            throw new ValidationFailureException("Only CSV files are allowed.");
        }
        if (file.getSize() > 50L * 1024L * 1024L) {
            throw new ValidationFailureException("File size exceeds 50MB limit.");
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private record HeaderCheck(int rowCount) {
    }
}
