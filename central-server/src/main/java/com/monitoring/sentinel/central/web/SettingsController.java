package com.monitoring.sentinel.central.web;

import com.monitoring.sentinel.central.alerting.AlertSettingsEntity;
import com.monitoring.sentinel.central.alerting.AlertSettingsRepository;
import com.monitoring.sentinel.central.security.AdminUserEntity;
import com.monitoring.sentinel.central.security.AdminUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SettingsController {

	private final AdminUserRepository adminUserRepository;
	private final AlertSettingsRepository alertSettingsRepository;
	private final PasswordEncoder passwordEncoder;

	public SettingsController(
			AdminUserRepository adminUserRepository,
			AlertSettingsRepository alertSettingsRepository,
			PasswordEncoder passwordEncoder) {
		this.adminUserRepository = adminUserRepository;
		this.alertSettingsRepository = alertSettingsRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/settings")
	public String settings(Model model) {
		model.addAttribute("alertSettings",
				alertSettingsRepository.findById(AlertSettingsEntity.SINGLETON_ID)
						.orElseGet(AlertSettingsEntity::new));
		return "settings";
	}

	@PostMapping("/settings/password")
	public String changePassword(
			Authentication authentication,
			@RequestParam String currentPassword,
			@RequestParam String newPassword,
			@RequestParam String confirmPassword,
			Model model) {
		AdminUserEntity admin = adminUserRepository.findById(authentication.getName()).orElseThrow();

		String error = null;
		if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
			error = "Current password is incorrect.";
		} else if (!newPassword.equals(confirmPassword)) {
			error = "New password and confirmation don't match.";
		} else if (newPassword.length() < 8) {
			error = "New password must be at least 8 characters.";
		}

		model.addAttribute("alertSettings",
				alertSettingsRepository.findById(AlertSettingsEntity.SINGLETON_ID)
						.orElseGet(AlertSettingsEntity::new));

		if (error != null) {
			model.addAttribute("passwordError", error);
			return "settings";
		}

		admin.setPasswordHash(passwordEncoder.encode(newPassword));
		adminUserRepository.save(admin);
		model.addAttribute("passwordSuccess", true);
		return "settings";
	}

	@PostMapping("/settings/email")
	public String saveEmailSettings(
			@ModelAttribute AlertSettingsEntity form,
			@RequestParam(required = false) String smtpPassword,
			Model model) {
		AlertSettingsEntity existing = alertSettingsRepository.findById(AlertSettingsEntity.SINGLETON_ID)
				.orElseGet(AlertSettingsEntity::new);
		existing.setEmailEnabled(form.isEmailEnabled());
		existing.setSmtpHost(form.getSmtpHost());
		existing.setSmtpPort(form.getSmtpPort());
		existing.setSmtpUsername(form.getSmtpUsername());
		existing.setFromAddress(form.getFromAddress());
		existing.setToAddress(form.getToAddress());
		// Blank means "leave unchanged" - the page never re-displays the stored password,
		// so an empty submit shouldn't wipe out a previously saved one.
		if (smtpPassword != null && !smtpPassword.isBlank()) {
			existing.setSmtpPassword(smtpPassword);
		}
		alertSettingsRepository.save(existing);

		model.addAttribute("alertSettings", existing);
		model.addAttribute("emailSuccess", true);
		return "settings";
	}
}
