# AI-Powered Code Debugger & Explainer

[![GitHub Repo](https://img.shields.io/badge/repo-AI--Debugger-blue)]()

## Overview
AI-Powered Code Debugger & Explainer is a **lightweight web-based tool** designed to help developers **debug and understand code**. It supports multiple programming languages (Java, Python, C) and provides **error detection, suggested fixes, and explanations**, along with **interactive visualizations of code execution**.

## Features
- Detects **syntax and logical errors** in code.
- Suggests **corrected code with explanations**.
- Interactive **code execution visualization**.
- Built-in **frontend code editor**.
- Two main actions:
  - `Run` – Execute code.
  - `AI Debug` – Detect errors, provide explanations, and fixes.
- Supports **manual code submission** with optional input.

## Project Structure
AI-Powered-Code-Debugger/
│
├─ frontend/
│ ├─ index.html # HTML page for frontend
│ ├─ style.css # Styles for the interface
│ └─ script.js # JavaScript logic
│
├─ backend/
│ ├─ src/main/java/... # Java Spring Boot source code
│ └─ pom.xml # Maven build file
│
├─ .gitignore # Git ignore rules
└─ README.md # Project documentation


## Installation and Setup

1. **Clone the repository:**
```bash
git clone https://github.com/Sufyan-10/AI-Powered-Code-Debugger.git

2. **Navigate to the backend folder and install dependencies:**
cd backend
mvn clean install

3. Run the Spring Boot backend:
mvn spring-boot:run

4. Open frontend in browser:
Open frontend/index.html in a web browser to use the interface.
