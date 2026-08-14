package com.practice.springboot.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class UserController {

    @GetMapping(path="/")
    public String home(){
        return "index";
    }

    @RequestMapping(path="/login")
    public String login(){
        return "login";
    }

    @RequestMapping(path="/logout")
    public String logout(){
        return "login";
    }
}
