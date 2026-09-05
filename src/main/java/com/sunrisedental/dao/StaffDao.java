package com.sunrisedental.dao;

import com.sunrisedental.model.StaffMember;

import java.sql.SQLException;
import java.util.List;

public interface StaffDao {

    StaffMember create(
            StaffMember staffMember,
            String passwordHash
    ) throws SQLException;

    List<StaffMember> search(
            String searchTerm,
            boolean includeInactive
    ) throws SQLException;

    boolean usernameExists(String username)
            throws SQLException;

    boolean emailExists(String email)
            throws SQLException;

    boolean registrationNumberExists(
            String registrationNumber
    ) throws SQLException;
}
