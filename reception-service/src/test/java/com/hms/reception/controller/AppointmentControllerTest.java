package com.hms.reception.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.reception.dto.AppointmentCreateRequest;
import com.hms.reception.entity.Appointment;
import com.hms.reception.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

    @Test
    void bookAppointment_withJsonBody_returns201() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setPatientId(patientId);
        request.setDoctorId(doctorId);
        request.setAppointmentDate(LocalDate.of(2026, 3, 1));
        request.setFromTime(LocalTime.of(9, 0));
        request.setToTime(LocalTime.of(9, 15));

        Appointment savedAppointment = Appointment.builder()
                .doctorId(doctorId)
                .appointmentDate(LocalDate.of(2026, 3, 1))
                .fromTime(LocalTime.of(9, 0))
                .toTime(LocalTime.of(9, 15))
                .status("scheduled")
                .build();
        savedAppointment.setId(UUID.randomUUID());

        when(appointmentService.bookAppointment(any(Appointment.class), any()))
                .thenReturn(savedAppointment);

        mockMvc.perform(post("/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("scheduled"));
    }

    @Test
    void bookAppointment_rejectsMultipartFormData() throws Exception {
        // Without explicit consumes restriction, Spring won't return 415
        // but the endpoint will fail to process multipart as JSON
        mockMvc.perform(post("/appointments")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("doctorId", UUID.randomUUID().toString())
                .param("appointmentDate", "2026-03-01")
                .param("fromTime", "09:00")
                .param("toTime", "09:15"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void bookAppointment_withMissingRequiredFields_returns400() throws Exception {
        AppointmentCreateRequest request = new AppointmentCreateRequest();
        // Missing required doctorId, appointmentDate, fromTime, toTime

        mockMvc.perform(post("/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAppointmentById_returns200() throws Exception {
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = Appointment.builder()
                .doctorId(UUID.randomUUID())
                .appointmentDate(LocalDate.of(2026, 3, 1))
                .fromTime(LocalTime.of(9, 0))
                .toTime(LocalTime.of(9, 15))
                .status("scheduled")
                .build();
        appointment.setId(appointmentId);

        when(appointmentService.getAppointmentById(appointmentId)).thenReturn(appointment);

        mockMvc.perform(get("/appointments/{id}", appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("scheduled"));
    }

    @Test
    void cancelAppointment_returns204() throws Exception {
        UUID appointmentId = UUID.randomUUID();

        mockMvc.perform(delete("/appointments/{id}", appointmentId))
                .andExpect(status().isNoContent());
    }
}
