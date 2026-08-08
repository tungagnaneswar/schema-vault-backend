package com.schemavault.app.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Handles all outbound email delivery for the application.
 *
 * <p>
 * Emails are sent asynchronously via {@code @Async} so they never block
 * the HTTP response thread. The plain OTP value is <b>never logged</b>.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String fromAddress;

  /**
   * Sends an OTP email to the specified recipient.
   *
   * <p>
   * The OTP itself is embedded only in the email body and is never written
   * to any log output.
   *
   * @param to  recipient email address
   * @param otp plain 6-digit OTP (not logged, not persisted here)
   */
  @Async
  public void sendOtpEmail(String to, String otp) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromAddress);
      helper.setTo(to);
      helper.setSubject("Schema Vault — Your Password Reset OTP");
      helper.setText(buildOtpEmailHtml(otp), true);

      mailSender.send(message);
      log.info("OTP email dispatched to: {}", to);
    } catch (MessagingException e) {
      // Log the error but do NOT propagate — a mail failure must not
      // reveal whether the email address exists in the system.
      log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
    }
  }

  /**
   * Sends a registration OTP email to the specified recipient.
   *
   * @param to  recipient email address
   * @param otp plain 6-digit OTP
   */
  @Async
  public void sendRegistrationOtpEmail(String to, String otp) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(fromAddress);
      helper.setTo(to);
      helper.setSubject("Schema Vault — Account Verification OTP");
      helper.setText(buildRegistrationOtpEmailHtml(otp), true);

      mailSender.send(message);
      log.info("Registration OTP email dispatched to: {}", to);
    } catch (MessagingException e) {
      log.error("Failed to send registration OTP email to {}: {}", to, e.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private String buildOtpEmailHtml(String otp) {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>Password Reset OTP</title>
        </head>
        <body style="margin:0;padding:0;background:#f4f6fb;font-family:'Segoe UI',Arial,sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fb;padding:40px 0;">
            <tr>
              <td align="center">
                <table width="480" cellpadding="0" cellspacing="0"
                       style="background:#ffffff;border-radius:12px;overflow:hidden;
                              box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                  <!-- Header -->
                  <tr>
                    <td style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);
                               padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;
                                 letter-spacing:0.5px;">Schema Vault</h1>
                      <p style="margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">
                        Password Reset Request
                      </p>
                    </td>
                  </tr>
                  <!-- Body -->
                  <tr>
                    <td style="padding:40px;">
                      <p style="margin:0 0 16px;color:#374151;font-size:15px;line-height:1.6;">
                        We received a request to reset your Schema Vault password.
                        Use the OTP below to proceed. It expires in <strong>10 minutes</strong>.
                      </p>
                      <!-- OTP Box -->
                      <div style="text-align:center;margin:32px 0;">
                        <span style="display:inline-block;background:#f3f0ff;
                                     border:2px solid #667eea;border-radius:10px;
                                     padding:18px 48px;font-size:36px;font-weight:700;
                                     letter-spacing:10px;color:#4c1d95;
                                     font-family:'Courier New',monospace;">
                          %s
                        </span>
                      </div>
                      <p style="margin:0 0 24px;color:#6b7280;font-size:13px;line-height:1.6;">
                        If you did not request a password reset, you can safely ignore this email.
                        Your password will not change.
                      </p>
                      <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0;"/>
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        This is an automated message from Schema Vault. Please do not reply.
                      </p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(otp);
  }

  private String buildRegistrationOtpEmailHtml(String otp) {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>Account Verification OTP</title>
        </head>
        <body style="margin:0;padding:0;background:#f4f6fb;font-family:'Segoe UI',Arial,sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6fb;padding:40px 0;">
            <tr>
              <td align="center">
                <table width="480" cellpadding="0" cellspacing="0"
                       style="background:#ffffff;border-radius:12px;overflow:hidden;
                              box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                  <!-- Header -->
                  <tr>
                    <td style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);
                               padding:32px 40px;text-align:center;">
                      <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;
                                 letter-spacing:0.5px;">Schema Vault</h1>
                      <p style="margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">
                        Account Verification
                      </p>
                    </td>
                  </tr>
                  <!-- Body -->
                  <tr>
                    <td style="padding:40px;">
                      <p style="margin:0 0 16px;color:#374151;font-size:15px;line-height:1.6;">
                        Welcome to Schema Vault! To complete your registration, please use the OTP below.
                        It expires in <strong>10 minutes</strong>.
                      </p>
                      <!-- OTP Box -->
                      <div style="text-align:center;margin:32px 0;">
                        <span style="display:inline-block;background:#f3f0ff;
                                     border:2px solid #667eea;border-radius:10px;
                                     padding:18px 48px;font-size:36px;font-weight:700;
                                     letter-spacing:10px;color:#4c1d95;
                                     font-family:'Courier New',monospace;">
                          %s
                        </span>
                      </div>
                      <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0;"/>
                      <p style="margin:0;color:#9ca3af;font-size:12px;text-align:center;">
                        This is an automated message from Schema Vault. Please do not reply.
                      </p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(otp);
  }
}
