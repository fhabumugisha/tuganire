package com.tuganire.auth.mapper;

import com.tuganire.auth.dto.UserResponse;
import com.tuganire.auth.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder().id(user.getId()).email(user.getEmail()).firstName(user.getFirstName())
                .lastName(user.getLastName()).plan(user.getPlan()).createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt()).build();
    }
}
