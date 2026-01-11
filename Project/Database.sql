Drop database disaster_management;

CREATE DATABASE IF NOT EXISTS disaster_management;
USE disaster_management;

-- Camps table
CREATE TABLE camps (
    camp_id INT AUTO_INCREMENT PRIMARY KEY,
    camp_name VARCHAR(100) NOT NULL,
    camp_number INT UNIQUE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Resources table (camp-specific)
CREATE TABLE resources (
    resource_id INT AUTO_INCREMENT PRIMARY KEY,
    camp_id INT NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    quantity INT DEFAULT 0,
    unit VARCHAR(50),
    FOREIGN KEY (camp_id) REFERENCES camps(camp_id),
    UNIQUE KEY unique_camp_resource (camp_id, resource_name)
);

-- Requests table
CREATE TABLE requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    camp_id INT,
    resource_name VARCHAR(100) NOT NULL,
    amount INT NOT NULL,
    deadline_days INT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP NULL,
    FOREIGN KEY (camp_id) REFERENCES camps(camp_id)
);

-- Logs table
CREATE TABLE logs (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    camp_id INT,
    action_type VARCHAR(100),
    description TEXT,
    log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (camp_id) REFERENCES camps(camp_id)
);