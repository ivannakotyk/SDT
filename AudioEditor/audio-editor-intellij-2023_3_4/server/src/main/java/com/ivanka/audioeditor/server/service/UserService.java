package com.ivanka.audioeditor.server.service;

import com.ivanka.audioeditor.server.model.AppUser;
import com.ivanka.audioeditor.server.repo.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final AppUserRepository repo;

    public UserService(AppUserRepository repo) {
        this.repo = repo;
    }

    public AppUser findOrCreate(String name, String email) {
        if (name == null || email == null)
            throw new IllegalArgumentException("Name and email must not be null");

        String cleanName = name.trim();
        String cleanEmail = email.trim().toLowerCase();

        Optional<AppUser> existing = repo.findByUserEmail(cleanEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        AppUser newUser = new AppUser(cleanName, cleanEmail);
        return repo.save(newUser);
    }

    public AppUser get(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("User with id=" + id + " not found"));
    }
}
