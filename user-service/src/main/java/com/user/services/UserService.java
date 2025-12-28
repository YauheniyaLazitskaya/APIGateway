package com.user.services;

import com.common.dto.userDto.CreateUserDTO;
import com.common.dto.userDto.UpdateUserDTO;
import com.user.clients.AuthFeignClient;
import com.user.dto.usersDto.*;
import com.user.entities.User;
import com.user.exceptions.UserException;
import com.user.mappers.UserMapper;
import com.user.repositories.UserRepository;
import com.user.specifications.UserSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthFeignClient authClient;


    @Transactional
    public UserDTO createUser(CreateUserDTO createUserDTO){
        if (userRepository.findByEmail(createUserDTO.getEmail()).isPresent())
            throw new UserException("User with this email (" + createUserDTO.getEmail() + ") already exists");
        String keycloakId = null;
        try{
            keycloakId = authClient.createUser(createUserDTO);
            User user = userMapper.toEntity(createUserDTO);
            user.setKeycloakId(keycloakId);
            User savedUser = userRepository.save(user);
            return userMapper.toDto(savedUser);
        }catch (Exception e){
            if (keycloakId != null) {
                try {
                    authClient.deleteUser(keycloakId);
                } catch (Exception ex) {
                    throw new UserException("Registration failed. Failed to rollback user in Keycloak.");
                }
            }
            throw new UserException("Registration failed. Please try again later.");
        }
    }

    @CachePut(value = "users", key = "#userId")
    @Transactional
    public UserDTO updateUserById(Integer userId, UpdateUserDTO newUserDTO){
        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("User not found with id: " + userId));
        if(StringUtils.hasText(newUserDTO.getEmail())){
            Optional<User> existingUser = userRepository.findByEmail(newUserDTO.getEmail());
            if(existingUser.isPresent())
                if(!existingUser.get().getId().equals(userId))
                    throw new UserException("User with this email (" + newUserDTO.getEmail() + ") already exists");
        }
        userMapper.updateUserFromDto(newUserDTO, userToUpdate);
        newUserDTO.setName(userToUpdate.getName());
        newUserDTO.setSurname(userToUpdate.getSurname());
        newUserDTO.setEmail(userToUpdate.getEmail());
        newUserDTO.setBirthDate(userToUpdate.getBirthDate());
        if (userToUpdate.getKeycloakId() != null) {
            try {
                authClient.updateUser(userToUpdate.getKeycloakId(), newUserDTO);
            }catch (Exception e){
                throw new UserException("Failed to update user in Keycloak.");
            }
        }else
            throw new UserException("Registration failed. User not found in Keycloak.");
        User updatedUser = userRepository.save(userToUpdate);
        return userMapper.toDto(updatedUser);
    }

    @Cacheable(value = "users", key = "#userId")
    @Transactional(readOnly = true)
    public UserDTO getUserById(Integer userId){
        User user = userRepository.findById(userId).orElseThrow(()
                -> new UserException("User not found with id: " + userId));
        return userMapper.toDto(user);
    }

    @Cacheable(value = "users", key = "#keycloakId")
    @Transactional(readOnly = true)
    public UserDTO getUserByKeycloakId(String keycloakId){
        User user = userRepository.findByKeycloakId(keycloakId).orElseThrow(()
                -> new UserException("User not found with keycloak id: " + keycloakId));
        return userMapper.toDto(user);
    }

    @Cacheable(value = "users", key = "#email")
    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException("User not found with email: " + email));
        return userMapper.toDto(user);
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(UserFilterDTO filter,int page, int size){
        Specification<User> spec = Specification.where(null);
        if (filter != null) {
            spec = spec.and(UserSpecification.hasName(filter.getName()))
                    .and(UserSpecification.hasSurname(filter.getSurname()))
                    .and(UserSpecification.hasEmail(filter.getEmail()))
                    .and(UserSpecification.isActive(filter.getActive()));
        }
        return userRepository.findAll(spec, PageRequest.of(page, size, Sort.by("name").
                and(Sort.by("surname")))).map(userMapper::toDto);
    }

    @CachePut(value = "users", key = "#userId")
    @Transactional
    public UserDTO setActivityUserById(Integer userId, Boolean status){
        User user = userRepository.findById(userId).orElseThrow(()
                -> new UserException("User not found with id: " + userId));
        user.setActive(status);
        return userMapper.toDto(userRepository.save(user));
    }

    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public void deleteUserById(Integer userId){
        User user = userRepository.findById(userId).orElseThrow(()
                -> new UserException("User not found with id: " + userId));
        if (user.getKeycloakId() != null) {
            authClient.deleteUser(user.getKeycloakId());
        }else
            throw new UserException("Registration failed. User not found in Keycloak.");
        userRepository.deleteById(userId);
    }
}
