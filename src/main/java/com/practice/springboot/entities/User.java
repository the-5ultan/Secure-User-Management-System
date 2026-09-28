package com.practice.springboot.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Audited    ;

@Entity
@Data
@Audited.Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String username;

    private String password;

    private String email;

    @Enumerated(EnumType.STRING)
    private String provider;

    private String providerId;
}
