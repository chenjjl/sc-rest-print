package com.chen.service;

import com.alibaba.fastjson.JSON;
import com.chen.model.Order;
import com.chen.model.OrderGood;
import com.chen.model.TemplateModel;
import com.chen.utils.ZkStringSerializer;
import org.I0Itec.zkclient.ZkClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;

import javax.annotation.PostConstruct;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Sides;
import java.awt.print.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class PrintOrderServiceImpl {

    @Value("${rest.school}")
    private String schoolName;

    @Value("${rest.restaurant}")
    private String restName;

    @Value("${rest.window}")
    private String windowName;

    @Value("${rest.printFilePath}")
    private String printFilePath;

    @Value("${rest.printerName}")
    private String printerName;

    @Value("${zk.host}")
    private String zkHost;

    @Autowired
    private TemplateEngine engine;

    @PostConstruct
    private void print() {
        ZkClient zkClient = new ZkClient(zkHost, 30000, 20000, new ZkStringSerializer());
        String nodeName = "/" + schoolName + "/" + restName + "/" + windowName;
        zkClient.subscribeChildChanges(nodeName, (parentPath, currentChilds) -> {
            for (String child : currentChilds) {
                String data = zkClient.readData(parentPath + "/" + child);
                Order order = JSON.parseObject(data, Order.class);
                OrderGood[] orderGoods = JSON.parseObject(order.getGoods(), OrderGood[].class);
                doPrint(order, orderGoods);
                zkClient.deleteRecursive(parentPath + "/" + child);
            }
        });
    }

    public void doPrint(Order order, OrderGood[] orderGoods) {
        List<OrderGood> myOrder = new ArrayList<>();
        if (order.getSchoolName().equals(schoolName) && order.getRestName().equals(restName)) {
            Arrays.stream(orderGoods).forEach(good -> {
                if (good.getWindowName().equals(windowName)) {
                    myOrder.add(good);
                }
            });
        }
        if (myOrder.size() != 0) {
            generatePdf(myOrder, order);
        }
    }

    private void generatePdf(List<OrderGood> orderGoods, Order order) {
        TemplateModel model = new TemplateModel(engine,"order.html");
        model.setOrder(order);
        model.setOrderGoodList(orderGoods);
        String pdfName = order.getId() + ".pdf";
        model.parse2Pdf(printFilePath + pdfName);
        printPdf(printFilePath + pdfName);
    }

    private void printPdf(String filePath) {
        File file = new File(filePath);
        PDDocument document = null;
        try {
            document = PDDocument.load(file);
            PrinterJob printJob = PrinterJob.getPrinterJob();
            printJob.setJobName(file.getName());
            if (printerName != null) {
                // ????????????????????????
                //??????????????????????????????????????????
                PrintService[] printServices = PrinterJob.lookupPrintServices();
                if(printServices == null || printServices.length == 0) {
                    System.out.print("??????????????????????????????????????????????????????");
                    return ;
                }
                PrintService printService = null;
                //?????????????????????
                for (int i = 0;i < printServices.length; i++) {
                    System.out.println(printServices[i].getName());
                    if (printServices[i].getName().contains(printerName)) {
                        printService = printServices[i];
                        break;
                    }
                }
                if(printService!=null){
                    printJob.setPrintService(printService);
                }else{
                    System.out.print("?????????????????????????????????" + printerName + "???????????????????????????");
                    return ;
                }
            }
            //?????????????????????
            PDFPrintable pdfPrintable = new PDFPrintable(document, Scaling.ACTUAL_SIZE);
            //??????????????????
            Book book = new Book();
            PageFormat pageFormat = new PageFormat();
            //??????????????????
            pageFormat.setOrientation(PageFormat.PORTRAIT);//??????
            pageFormat.setPaper(getPaper());//????????????
            book.append(pdfPrintable, pageFormat, document.getNumberOfPages());
            printJob.setPageable(book);
            printJob.setCopies(1);//??????????????????
            //??????????????????
            HashPrintRequestAttributeSet pars = new HashPrintRequestAttributeSet();
            pars.add(Sides.ONE_SIDED); //???????????????
            printJob.print(pars);
        } catch (IOException | PrinterException e) {
            e.printStackTrace();
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static Paper getPaper() {
        Paper paper = new Paper();
        // ?????????A4??????????????????????????????????????? 595, 842
        int width = 500;
        int height = 842;
        // ?????????????????????????????????10mm??????????????? 28px
        int marginLeft = 5;
        int marginRight = 0;
        int marginTop = 0;
        int marginBottom = 0;
        paper.setSize(width, height);
        // ?????????????????????????????????????????????????????????
        paper.setImageableArea(marginLeft, marginRight, width - (marginLeft + marginRight), height - (marginTop + marginBottom));
        return paper;
    }
}
