package app.simsmartgsm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller để serve giao diện web cho Call Management
 */
@Controller
@RequestMapping("/call-ui")
public class CallWebController {

    @GetMapping
    public String callManagementPage() {
        return "redirect:/call-management.html";
    }
}
