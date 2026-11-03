package com.worknest.servlet;

import com.worknest.util.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.worknest.util.PasswordUtil;

import javax.servlet.http.HttpSession;
public class LoginServlet extends HttpServlet {
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
		req.setCharacterEncoding("UTF-8");
		String email = req.getParameter("email");
		String password = req.getParameter("password");
		if(email == null || email.isBlank()) {
			res.sendRedirect(req.getContextPath() + "/login.html?error=input");
			return;
		}
		
		if (email.length() > 255 || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
			res.sendRedirect(req.getContextPath() + "/login.html?error=input");
			return;
    	}
		
		if(password == null || password.isBlank()) {
			res.sendRedirect(req.getContextPath() + "/login.html?error=input");
			return;
		}
		
		if(password.length()>100) {
			res.sendRedirect(req.getContextPath() + "/login.html?error=input");
			return;
		}
		
		String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbUsername == null || dbUsername.isBlank() || dbPassword == null || dbPassword.isEmpty()) {
        	log("Database credentials are not configured.");
        	res.sendRedirect(req.getContextPath() + "/login.html?error=server");
        	return;
        }
        
        long userId = 0;
        String fullName = null;
        String storedHash = null;
        String role = null;
        
        boolean userFound = false;
        
        try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			String sql = "SELECT  user_id, full_name, password_hash, role FROM users where email = ?";
			try(Connection connection = DatabaseUtil.getConnection();
					PreparedStatement preparedStatement = connection.prepareStatement(sql)){
		        preparedStatement.setString(1, email);
		        
		        try(ResultSet resultSet = preparedStatement.executeQuery()){
		        	if(resultSet.next()) {
		        		userId = resultSet.getLong("user_id");
		        		fullName = resultSet.getString("full_name");
		        		storedHash = resultSet.getString("password_hash");
		        		role = resultSet.getString("role");
		        		
		        		userFound = true;
		        	}
		        }
			} 
			
		} catch (ClassNotFoundException e) {
			log("MySQL JDBC driver not found.", e);
			res.sendRedirect(req.getContextPath() + "/login.html?error=server");
			return;
		}
        catch (SQLException e) {
        	log("Database error during login.", e);
        	res.sendRedirect(req.getContextPath() + "/login.html?error=server");
        	return;
		}
        
        if (!userFound) {
        	res.sendRedirect(req.getContextPath() + "/login.html?error=invalid");
        	return;
        }

        boolean passwordValid =
                PasswordUtil.verifyPassword(password, storedHash);

        if (!passwordValid) {
        	res.sendRedirect(req.getContextPath() + "/login.html?error=invalid");
        	return;
        }
        HttpSession session = req.getSession();
        req.changeSessionId();
        com.worknest.util.CsrfTokenUtil.rotateToken(session);

        session.setAttribute("userId", userId);
        session.setAttribute("fullName", fullName);
        session.setAttribute("role", role);
        
        if ("FREELANCER".equals(role)) {
            res.sendRedirect(req.getContextPath() + "/dashboard/freelancer/index.html");

        } else if ("CLIENT".equals(role)) {
            res.sendRedirect(req.getContextPath() + "/dashboard/client/index.html");

        } else {
            session.invalidate();
            res.sendRedirect(req.getContextPath() + "/login.html?error=server");
        }
	}

}
