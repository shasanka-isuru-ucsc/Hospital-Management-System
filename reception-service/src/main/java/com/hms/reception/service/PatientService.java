package com.hms.reception.service;

import com.hms.reception.entity.Patient;
import com.hms.reception.exception.BusinessException;
import com.hms.reception.exception.ResourceNotFoundException;
import com.hms.reception.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final MinioService minioService;

    @Transactional
    public Patient registerPatient(Patient patientDTO) {
        log.info("Registering new patient with mobile: {}", patientDTO.getMobile());

        Optional<Patient> existing = patientRepository.findByMobile(patientDTO.getMobile());
        if (existing.isPresent()) {
            throw new BusinessException("DUPLICATE_MOBILE",
                    "A patient with mobile " + patientDTO.getMobile() + " is already registered", 409);
        }

        String nextPatientNumber = generateNextPatientNumber();
        patientDTO.setPatientNumber(nextPatientNumber);
        patientDTO.setStatus("active");
        patientDTO.setCreatedAt(ZonedDateTime.now());

        return patientRepository.save(patientDTO);
    }

    public Page<Patient> getAllPatients(String search, String status, Pageable pageable) {
        Page<Patient> patients = patientRepository.findAllByFilters(search, status, pageable);
        patients.forEach(this::enrichWithPresignedUrl);
        return patients;
    }

    public List<Patient> searchPatients(String query) {
        return patientRepository.searchByNameOrMobile(query);
    }

    public Patient getPatientById(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No patient with id " + id));
        enrichWithPresignedUrl(patient);
        return patient;
    }

    @Transactional
    public Patient updatePatient(UUID id, Patient updateData) {
        Patient patient = getPatientById(id);

        if (updateData.getFirstName() != null)
            patient.setFirstName(updateData.getFirstName());
        if (updateData.getLastName() != null)
            patient.setLastName(updateData.getLastName());
        if (updateData.getMobile() != null && !updateData.getMobile().equals(patient.getMobile())) {
            Optional<Patient> existing = patientRepository.findByMobile(updateData.getMobile());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new BusinessException("DUPLICATE_MOBILE",
                        "A patient with mobile " + updateData.getMobile() + " is already registered", 409);
            }
            patient.setMobile(updateData.getMobile());
        }
        if (updateData.getEmail() != null)
            patient.setEmail(updateData.getEmail());
        if (updateData.getAddress() != null)
            patient.setAddress(updateData.getAddress());
        if (updateData.getBloodGroup() != null)
            patient.setBloodGroup(updateData.getBloodGroup());
        if (updateData.getTriage() != null)
            patient.setTriage(updateData.getTriage());
        if (updateData.getStatus() != null)
            patient.setStatus(updateData.getStatus());

        return patientRepository.save(patient);
    }

    private synchronized String generateNextPatientNumber() {
        Long maxNumber = patientRepository.findMaxPatientNumber();
        long nextId = (maxNumber == null ? 0 : maxNumber) + 1;
        return String.format("R%05d", nextId);
    }

    private void enrichWithPresignedUrl(Patient patient) {
        if (patient.getAvatarUrl() != null && !patient.getAvatarUrl().startsWith("http")) {
            patient.setAvatarUrl(minioService.getPresignedUrl(patient.getAvatarUrl()));
        }
    }
}
