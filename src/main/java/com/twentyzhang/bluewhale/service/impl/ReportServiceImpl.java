package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.service.ReportService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReportServiceImpl extends ServiceImpl<OrderMapper, Order> implements ReportService {

    @Override
    public StoreOrderReportResponse getStoreOrderReport(Long storeId, LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public GlobalOrderReportResponse getGlobalOrderReport(LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
