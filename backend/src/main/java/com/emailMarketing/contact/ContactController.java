package com.emailMarketing.contact;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.emailMarketing.subscription.UserRepository;

import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/contacts")
public class ContactController {
    private final ContactService contactService; private final UserRepository userRepository;
    public ContactController(ContactService contactService, UserRepository userRepository){this.contactService=contactService; this.userRepository=userRepository;}

    public record CreateContact(@Email String email, String firstName, String lastName, String segment){}

    @GetMapping
    public List<Contact> list(@AuthenticationPrincipal UserDetails principal, @RequestParam(required=false) String segment){
        return contactService.list(resolveUserId(principal), segment);
    }

    @PostMapping
    public Contact create(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody CreateContact req){
        Contact c = new Contact();
        c.setUserId(resolveUserId(principal));
        c.setEmail(req.email()); c.setFirstName(req.firstName()); c.setLastName(req.lastName()); c.setSegment(req.segment());
        return contactService.add(c);
    }

    private Long resolveUserId(UserDetails principal){
        return userRepository.findByUsername(principal.getUsername()).map(u->u.getId()).orElseThrow();
    }
}
