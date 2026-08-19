const form = document.getElementById("loginForm");
const usernameInput = document.getElementById("username");
const passwordInput = document.getElementById("password");
const usernameError = document.getElementById("usernameError");
const passwordError = document.getElementById("passwordError");
const loginMessage = document.getElementById("loginMessage");
const loginButton = document.getElementById("loginButton");

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    usernameError.textContent = "";
    passwordError.textContent = "";
    loginMessage.textContent = "";

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    let valid = true;

    if (!/^[A-Za-z0-9._-]{4,50}$/.test(username)) {
        usernameError.textContent =
            "Enter a valid username.";
        valid = false;
    }

    if (!password) {
        passwordError.textContent =
            "Password is required.";
        valid = false;
    }

    if (!valid) {
        return;
    }

    loginButton.disabled = true;
    loginButton.textContent = "Signing in...";

    try {
        const formData = new URLSearchParams();
        formData.append("username", username);
        formData.append("password", password);

        const response = await fetch(
            "api/v1/auth/login",
            {
                method: "POST",
                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },
                credentials: "same-origin",
                body: formData
            }
        );

        const result = await response.json();

        if (!response.ok || !result.success) {
            throw new Error(
                result.message || "Login failed"
            );
        }

        window.location.replace("dashboard.html");

    } catch (error) {
        loginMessage.textContent = error.message;

    } finally {
        loginButton.disabled = false;
        loginButton.textContent = "Sign in";
    }
});