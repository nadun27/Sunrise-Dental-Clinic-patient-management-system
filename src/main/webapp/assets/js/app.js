const checkServerButton = document.getElementById("checkServerButton");
const serverResult = document.getElementById("serverResult");

checkServerButton.addEventListener("click", async () => {
    checkServerButton.disabled = true;
    serverResult.textContent = "Checking Java server...";

    try {
        const response = await fetch("api/v1/health");
        const result = await response.json();

        if (!response.ok) {
            throw new Error("The server returned an error.");
        }

        serverResult.textContent =
            `${result.application} server status: ${result.status}`;

    } catch (error) {
        serverResult.textContent =
            "Server unavailable. Deploy the WAR file to Tomcat first.";

    } finally {
        checkServerButton.disabled = false;
    }
});