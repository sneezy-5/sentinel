document.addEventListener("DOMContentLoaded", () => {
	const deleteBtn = document.getElementById("delete-server-btn");
	if (!deleteBtn) {
		return;
	}

	deleteBtn.addEventListener("click", async () => {
		const serverId = deleteBtn.dataset.serverId;
		const serverName = deleteBtn.dataset.serverName;
		if (!window.confirm("Delete \"" + serverName + "\" and all its stored metrics/logs? This can't be undone.")) {
			return;
		}

		const csrfToken = document.querySelector('meta[name="_csrf"]');
		const csrfHeaderName = document.querySelector('meta[name="_csrf_header"]');
		const headers = csrfToken && csrfHeaderName ? { [csrfHeaderName.content]: csrfToken.content } : {};

		deleteBtn.disabled = true;
		try {
			const response = await fetch("/api/servers/" + encodeURIComponent(serverId), {
				method: "DELETE",
				headers,
			});
			if (!response.ok) {
				window.alert("Failed to delete server (HTTP " + response.status + ")");
				deleteBtn.disabled = false;
				return;
			}
			window.location.href = "/";
		} catch (err) {
			window.alert("Network error: " + err.message);
			deleteBtn.disabled = false;
		}
	});
});
