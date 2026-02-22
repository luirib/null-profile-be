package ch.nullprofile.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    /**
     * TODO: Implement login page
     * This should show WebAuthn authentication UI
     */
    @GetMapping("/login")
    @ResponseBody
    public String login() {
        return "Login page - TODO: Implement WebAuthn authentication UI. " +
               "After successful authentication, redirect back to /authorize to complete the flow.";
    }
}
