package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import com.example.supplychain.dto.LoginRequest;
import com.example.supplychain.dto.RegisterRequest;
import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;
import com.example.supplychain.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceSecurityTest {

    private final UserAccountRepository repository =
            mock(UserAccountRepository.class);

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private final AuthService service = new AuthService(repository, encoder);

    @Test
    void registrationStoresHashAndAssignsStaff() {
        when(repository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("b08_hash_test");
        request.setPassword("B08_Test_Password!");

        UserAccount saved = service.register(request);

        assertNotEquals(request.getPassword(), saved.getPassword());
        assertTrue(encoder.matches(request.getPassword(), saved.getPassword()));
        assertEquals(Role.WAREHOUSE_STAFF, saved.getRole());
    }

    @Test
    void loginAcceptsPasswordMatchingStoredHash() {
        UserAccount account = new UserAccount();
        account.setUsername("b08_hash_test");
        account.setPassword(encoder.encode("B08_Test_Password!"));

        when(repository.findByUsername("b08_hash_test"))
                .thenReturn(Optional.of(account));

        assertSame(account, service.login(
                loginRequest("b08_hash_test", "B08_Test_Password!")));
    }

    @Test
    void wrongPasswordAndUnknownUserReturnSameMessage() {
        UserAccount account = new UserAccount();
        account.setUsername("b08_hash_test");
        account.setPassword(encoder.encode("B08_Test_Password!"));

        when(repository.findByUsername("b08_hash_test"))
                .thenReturn(Optional.of(account));
        when(repository.findByUsername("b08_unknown"))
                .thenReturn(Optional.empty());

        BadCredentialsException wrongPassword = assertThrows(
                BadCredentialsException.class,
                () -> service.login(loginRequest("b08_hash_test", "wrong")));

        BadCredentialsException unknownUser = assertThrows(
                BadCredentialsException.class,
                () -> service.login(loginRequest("b08_unknown", "wrong")));

        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}