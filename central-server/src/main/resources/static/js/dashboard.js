// Spring Security's CSRF protection covers session-authenticated POSTs, including this
// fetch() call - the token is exposed via meta tags (see fragments/layout.html) since
// this is a fetch, not a plain form submit that could carry a hidden input instead.
function csrfHeader() {
	const token = document.querySelector('meta[name="_csrf"]');
	const header = document.querySelector('meta[name="_csrf_header"]');
	if (!token || !header) {
		return {};
	}
	return { [header.content]: token.content };
}

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
			response = await fetch("/api/servers?" + params.toString(), {
				method: "POST",
				headers: csrfHeader(),
			});
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
