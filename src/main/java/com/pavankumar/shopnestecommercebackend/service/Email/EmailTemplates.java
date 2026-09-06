package com.pavankumar.shopnestecommercebackend.service.Email;

import java.math.BigDecimal;

public class EmailTemplates {

    public static String orderConfirmation(String userName, Long orderId, BigDecimal totalAmount) {
        String inner = """
                <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Order Confirmed</p>
                <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Thanks, %s!</h2>
                <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
                  Your order has been placed successfully. We'll email you again once it ships.
                </p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f7f7fa; border-radius:6px; margin-bottom:8px;">
                  <tr>
                    <td style="padding:16px 20px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif; padding-bottom:8px;">Order ID</td>
                          <td style="font-size:13px; color:#1a1a2e; font-weight:bold; font-family: Arial, Helvetica, sans-serif; text-align:right; padding-bottom:8px;">#%d</td>
                        </tr>
                        <tr>
                          <td style="font-size:13px; color:#6b6b76; font-family: Arial, Helvetica, sans-serif;">Total Paid</td>
                          <td style="font-size:16px; color:#1a1a2e; font-weight:bold; font-family: Arial, Helvetica, sans-serif; text-align:right;">₹%s</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(userName, orderId, totalAmount.toPlainString());
        return shell(inner, "Your ShopNest order #" + orderId + " is confirmed.");
    }

    public static String passwordReset(String userName, String resetLink) {
        String inner = """
            <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Password Reset</p>
            <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Hi %s,</h2>
            <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
              We received a request to reset your ShopNest password. This link expires in 30 minutes.
            </p>
            <table role="presentation" cellpadding="0" cellspacing="0">
              <tr>
                <td style="background-color:#1a1a2e; border-radius:6px;">
                  <a href="%s" style="display:inline-block; padding:12px 24px; color:#ffffff; text-decoration:none; font-size:14px; font-family: Arial, Helvetica, sans-serif; font-weight:bold;">Reset Password</a>
                </td>
              </tr>
            </table>
            <p style="margin:20px 0 0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
              If you didn't request this, you can safely ignore this email.
            </p>
            """.formatted(userName, resetLink);
        return shell(inner, "Reset your ShopNest password");
    }

    public static String cancellationRequested(String userName, Long orderId, BigDecimal amount) {
        String inner = """
            <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Cancellation Received</p>
            <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Hi %s,</h2>
            <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
              We've received your cancellation request for order <strong>#%d</strong>.
              Your refund of ₹%s is now being processed and typically takes 5–7 business days to reflect.
            </p>
            <p style="margin:0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
              We'll email you again once the refund is complete.
            </p>
            """.formatted(userName, orderId, amount.toPlainString());
        return shell(inner, "Your cancellation for order #" + orderId + " is being processed.");
    }

    public static String refundProcessed(String userName, Long orderId, BigDecimal amount) {
        String inner = """
            <p style="margin:0 0 4px; font-size:13px; color:#6b6b76; text-transform:uppercase; letter-spacing:0.5px;">Refund Processed</p>
            <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Hi %s,</h2>
            <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
              Your refund of <strong>₹%s</strong> for order <strong>#%d</strong> has been processed successfully.
            </p>
            <p style="margin:0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
              It should reflect in your original payment method shortly, depending on your bank.
            </p>
            """.formatted(userName, amount.toPlainString(), orderId);
        return shell(inner, "Your refund for order #" + orderId + " is complete.");
    }

    public static String refundFailed(String userName, Long orderId, BigDecimal amount) {
        String inner = """
            <p style="margin:0 0 4px; font-size:13px; color:#b3261e; text-transform:uppercase; letter-spacing:0.5px;">Refund Failed</p>
            <h2 style="margin:0 0 16px; font-size:20px; color:#1a1a2e; font-family: Arial, Helvetica, sans-serif;">Hi %s,</h2>
            <p style="margin:0 0 20px; font-size:15px; color:#444450; line-height:1.5; font-family: Arial, Helvetica, sans-serif;">
              We were unable to process your refund of <strong>₹%s</strong> for order <strong>#%d</strong>.
              This can happen for bank-related reasons and needs manual review.
            </p>
            <p style="margin:0; font-size:13px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">
              Please reply to this email or contact our support team so we can resolve this for you.
            </p>
            """.formatted(userName, amount.toPlainString(), orderId);
        return shell(inner, "We couldn't process your refund for order #" + orderId + ".");
    }

    private static String shell(String innerContent, String preheaderText) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0; padding:0;">
                  <div style="display:none; max-height:0; overflow:hidden; opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f7;">
                    <tr>
                      <td align="center" style="padding:24px 12px;">
                        <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px; width:100%%; background-color:#ffffff; border-radius:8px; overflow:hidden;">
                          <tr>
                            <td style="background-color:#1a1a2e; padding:24px; text-align:center;">
                              <span style="color:#ffffff; font-size:22px; font-weight:bold; font-family: Arial, Helvetica, sans-serif; letter-spacing:0.5px;">ShopNest</span>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:32px 28px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color:#f0f0f3; padding:16px 28px; text-align:center;">
                              <span style="font-size:12px; color:#9a9aa5; font-family: Arial, Helvetica, sans-serif;">This is an automated message from ShopNest — please do not reply.</span>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(preheaderText, innerContent);
    }

}
