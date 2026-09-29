package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DataSource dataSource;

    @Test
    void liveness_isAlwaysOk() {
        assertThat(mvc.get().uri("/healthz")).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void readiness_isOkWhenTheDatabaseAnswers() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.isValid(1)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);

        assertThat(mvc.get().uri("/readyz")).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void readiness_isUnavailableWhenTheDatabaseIsUnreachable() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        assertThat(mvc.get().uri("/readyz"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson()
                .isStrictlyEqualTo("{\"status\":\"unavailable\",\"reason\":\"database unreachable\"}");
    }
}
