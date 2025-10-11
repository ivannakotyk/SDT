package com.ivanka.audioeditor.server.repo;
import com.ivanka.audioeditor.server.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUserEmail(String email);
}
