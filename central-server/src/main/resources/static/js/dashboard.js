document.addEventListener("DOMContentLoaded", () => {
	const form = document.getElementById("add-server-form");
	if (!form) {
		return;
	}

	const errorEl = document.getElementById("add-server-error");
	const resultEl = document.getElementById("add-server-result");
	const installCommandEl = document.getElementById("install-command");
	const doneButton = document.getElementById("add-server-done");

	form.addEventListener("submit", async (event) => {
		event.preventDefault();
		errorEl.hidden = true;

		const params = new URLSearchParams(new FormData(form));
		let response;
		try {
			response = await fetch("/api/servers?" + params.toString(), { method: "POST" });
		} catch (networkError) {
			errorEl.textContent = "Network error: " + networkError.message;
			errorEl.hidden = false;
			return;
		}

		if (!response.ok) {
			errorEl.textContent = "Failed to create server (HTTP " + response.status + ")";
			errorEl.hidden = false;
			return;
		}

		const data = await response.json();
		installCommandEl.textContent = data.installCommand;
		form.hidden = true;
		resultEl.hidden = false;
	});

	doneButton.addEventListener("click", () => {
		window.location.reload();
	});
});
