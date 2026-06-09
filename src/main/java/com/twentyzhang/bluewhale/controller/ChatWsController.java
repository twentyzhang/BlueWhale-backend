package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StaffSendRequest;
import com.twentyzhang.bluewhale.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;

    /** 买家发送：SEND /app/chat.customer.send */
    @MessageMapping("/chat.customer.send")
    public void customerSend(@Payload @Valid CustomerSendRequest request, Principal principal) {
        chatService.sendFromCustomer(principal(principal), request);
    }

    /** 客服回复：SEND /app/chat.staff.send */
    @MessageMapping("/chat.staff.send")
    public void staffSend(@Payload @Valid StaffSendRequest request, Principal principal) {
        chatService.sendFromStaff(principal(principal), request);
    }

    /** SEND 处理异常回投到 /user/queue/errors。 */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception e) {
        return e.getMessage();
    }

    private com.twentyzhang.bluewhale.common.AuthUser principal(Principal principal) {
        return ((ChatPrincipal) principal).user();
    }
}
