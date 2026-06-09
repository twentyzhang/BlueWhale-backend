package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StaffSendRequest;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
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

    /**
     * SEND 处理异常回投到 /user/queue/errors。
     * 业务异常回友好信息；未预期异常记日志并回通用文案，避免向客户端泄漏内部细节或返回 null。
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception e) {
        if (e instanceof BusinessException be) {
            return be.getMessage();
        }
        log.error("WS 消息处理未预期异常", e);
        return "服务器内部错误，请稍后重试";
    }

    private AuthUser principal(Principal principal) {
        return ((ChatPrincipal) principal).user();
    }
}
