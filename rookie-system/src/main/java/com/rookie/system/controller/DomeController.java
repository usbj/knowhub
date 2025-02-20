package com.rookie.system.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "测试")
public class DomeController {

    @GetMapping("/hello")
    @Operation(summary = "测试")
    public String dome(){
        return "hello rookie";
    }

}
