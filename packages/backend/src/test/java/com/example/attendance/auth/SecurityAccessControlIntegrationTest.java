package com.example.attendance.auth;

import com.example.attendance.department.entity.Department;
import com.example.attendance.employee.entity.Employee;
import com.example.attendance.employee.entity.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAccessControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockHttpSession adminSession;
    private MockHttpSession employeeSession;
    private UUID departmentId;
    private UUID employeeId;

    @BeforeEach
    void setUp() throws Exception {
        departmentId = UUID.randomUUID();
        var department = Department.builder()
            .id(departmentId)
            .name("開発部")
            .build();
        entityManager.persist(department);

        var admin = Employee.builder()
            .id(UUID.randomUUID())
            .name("管理者")
            .email("admin@example.com")
            .password(passwordEncoder.encode("password123"))
            .department(department)
            .role(Role.ADMIN)
            .isManager(false)
            .hireDate(LocalDate.of(2024, 1, 1))
            .build();
        entityManager.persist(admin);

        employeeId = UUID.randomUUID();
        var employee = Employee.builder()
            .id(employeeId)
            .name("一般社員")
            .email("employee@example.com")
            .password(passwordEncoder.encode("password123"))
            .department(department)
            .role(Role.EMPLOYEE)
            .isManager(false)
            .hireDate(LocalDate.of(2024, 4, 1))
            .build();
        entityManager.persist(employee);
        entityManager.flush();

        adminSession = login("admin@example.com", "password123");
        employeeSession = login("employee@example.com", "password123");
    }

    @Nested
    @DisplayName("未認証アクセス → 401")
    class Unauthenticated {

        @Test
        @DisplayName("GET /api/auth/me")
        void getMe() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/departments")
        void getDepartments() throws Exception {
            mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/employees")
        void getEmployees() throws Exception {
            mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/attendance/clock-in")
        void clockIn() throws Exception {
            mockMvc.perform(post("/api/attendance/clock-in")
                    .with(csrf())
                    .param("employeeId", employeeId.toString()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/attendance/today")
        void todayStatus() throws Exception {
            mockMvc.perform(get("/api/attendance/today")
                    .param("employeeId", employeeId.toString()))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/attendance/all")
        void allAttendance() throws Exception {
            mockMvc.perform(get("/api/attendance/all")
                    .param("month", "2024-06"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PUT /api/attendance/{id}/memo")
        void updateMemo() throws Exception {
            mockMvc.perform(put("/api/attendance/{id}/memo", UUID.randomUUID())
                    .with(csrf())
                    .param("editorId", employeeId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"テスト\",\"version\":0}"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/attendance/{id}/memo/history")
        void getMemoEditHistory() throws Exception {
            mockMvc.perform(get("/api/attendance/{id}/memo/history", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("EMPLOYEE → ADMIN専用エンドポイントは403")
    class EmployeeAccessDenied {

        @Test
        @DisplayName("GET /api/employees → 403")
        void getEmployees() throws Exception {
            mockMvc.perform(get("/api/employees").session(employeeSession))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/employees/{id} → 403")
        void getEmployeeById() throws Exception {
            mockMvc.perform(get("/api/employees/{id}", employeeId)
                    .session(employeeSession))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/employees → 403")
        void createEmployee() throws Exception {
            mockMvc.perform(post("/api/employees")
                    .session(employeeSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/employees/{id} → 403")
        void updateEmployee() throws Exception {
            mockMvc.perform(put("/api/employees/{id}", employeeId)
                    .session(employeeSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PATCH /api/employees/{id}/retire → 403")
        void retireEmployee() throws Exception {
            mockMvc.perform(patch("/api/employees/{id}/retire", employeeId)
                    .session(employeeSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/departments → 403")
        void createDepartment() throws Exception {
            mockMvc.perform(post("/api/departments")
                    .session(employeeSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"営業部\"}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/departments/{id} → 403")
        void updateDepartment() throws Exception {
            mockMvc.perform(put("/api/departments/{id}", departmentId)
                    .session(employeeSession)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"営業部\"}"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/attendance/all → 403")
        void getAllAttendance() throws Exception {
            mockMvc.perform(get("/api/attendance/all")
                    .session(employeeSession)
                    .param("month", "2024-06"))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("EMPLOYEE → 認証済みエンドポイントは200")
    class EmployeeAllowed {

        @Test
        @DisplayName("GET /api/departments → 200")
        void getDepartments() throws Exception {
            mockMvc.perform(get("/api/departments").session(employeeSession))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/auth/me → 200")
        void getMe() throws Exception {
            mockMvc.perform(get("/api/auth/me").session(employeeSession))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/attendance/clock-in → 201")
        void clockIn() throws Exception {
            mockMvc.perform(post("/api/attendance/clock-in")
                    .session(employeeSession)
                    .with(csrf())
                    .param("employeeId", employeeId.toString()))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("GET /api/attendance/today → 200")
        void todayStatus() throws Exception {
            mockMvc.perform(get("/api/attendance/today")
                    .session(employeeSession)
                    .param("employeeId", employeeId.toString()))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/attendance/history → 200")
        void history() throws Exception {
            mockMvc.perform(get("/api/attendance/history")
                    .session(employeeSession)
                    .param("employeeId", employeeId.toString())
                    .param("month", "2024-06"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT /api/attendance/{id}/memo → 認証済みで403にならない")
        void updateMemo() throws Exception {
            // まず出勤打刻してレコードを作成
            var clockInResult = mockMvc.perform(post("/api/attendance/clock-in")
                    .session(employeeSession)
                    .with(csrf())
                    .param("employeeId", employeeId.toString()))
                .andExpect(status().isCreated())
                .andReturn();
            var body = clockInResult.getResponse().getContentAsString();
            var recordId = com.jayway.jsonpath.JsonPath.read(body, "$.id").toString();

            // メモ更新 — 認可が通ること(403にならないこと)を確認
            mockMvc.perform(put("/api/attendance/{id}/memo", recordId)
                    .session(employeeSession)
                    .with(csrf())
                    .param("editorId", employeeId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo\":\"更新テスト\",\"version\":0}"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/attendance/{id}/memo/history → 認証済みで403にならない")
        void getMemoEditHistory() throws Exception {
            mockMvc.perform(get("/api/attendance/{id}/memo/history", UUID.randomUUID())
                    .session(employeeSession))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("ADMIN → 全エンドポイントアクセス可能")
    class AdminAccess {

        @Test
        @DisplayName("GET /api/departments → 200")
        void getDepartments() throws Exception {
            mockMvc.perform(get("/api/departments").session(adminSession))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/employees → 200")
        void getEmployees() throws Exception {
            mockMvc.perform(get("/api/employees").session(adminSession))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/attendance/all → 200")
        void getAllAttendance() throws Exception {
            mockMvc.perform(get("/api/attendance/all")
                    .session(adminSession)
                    .param("month", "2024-06"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("permitAll エンドポイント")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /api/auth/login → 未認証でもアクセス可能")
        void login() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"admin@example.com\", \"password\": \"password123\"}"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/health → 未認証でもアクセス可能")
        void actuatorHealth() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("denyAll — 未定義のパスは拒否")
    class DenyAll {

        @Test
        @DisplayName("未定義のパスは認証済みでも403が返される")
        void undefinedPath_authenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/unknown").session(adminSession))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("未定義のパスは未認証で401が返される")
        void undefinedPath_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isUnauthorized());
        }
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) Objects.requireNonNull(result.getRequest().getSession());
    }
}
