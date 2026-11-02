package com.flowlane.identity;

import com.flowlane.common.security.CurrentUser;
import com.flowlane.identity.AuthDtos.ChangePasswordRequest;
import com.flowlane.identity.AuthDtos.UpdateProfileRequest;
import com.flowlane.identity.AuthDtos.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public UserDto me() {
        return users.me(CurrentUser.id());
    }

    @PatchMapping
    public UserDto update(@Valid @RequestBody UpdateProfileRequest req) {
        return users.update(CurrentUser.id(), req);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        users.changePassword(CurrentUser.id(), req);
    }
}
