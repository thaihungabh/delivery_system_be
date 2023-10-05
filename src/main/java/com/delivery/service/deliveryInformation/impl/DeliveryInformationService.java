package com.delivery.service.deliveryInformation.impl;

import com.delivery.DTO.TransportOrderResponse;
import com.delivery.DTO.rawDataFromEcommerce.deliveryInformation.request.DeliveryInformationRequest;
import com.delivery.DTO.rawDataFromEcommerce.deliveryInformation.response.DeliveryInformationByDistrict;
import com.delivery.entity.DeliveryInformationEntity;
import com.delivery.entity.EStatus;
import com.delivery.entity.RawEcommerceOrderEntity;
import com.delivery.entity.UserEntity;
import com.delivery.model.rawDataFromEcommerce.DeliveryInformation;
import com.delivery.repository.DeliveryInformationRepository;
import com.delivery.repository.UserRepository;
import com.delivery.service.deliveryInformation.IDeliveryInformationService;
import com.delivery.service.email.IEmailService;
import com.delivery.util.ResponseObject;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
public class DeliveryInformationService implements IDeliveryInformationService {
    private final DeliveryInformationRepository deliveryInformationRepository;
    private final UserRepository userRepository;
    private final IEmailService emailService;
    private final ModelMapper modelMapper;

    public DeliveryInformationService(DeliveryInformationRepository deliveryInformationRepository,
                                      ModelMapper modelMapper,
                                      UserRepository userRepository,
                                      IEmailService emailService) {
        this.deliveryInformationRepository = deliveryInformationRepository;
        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
    @Override
    public DeliveryInformation addDeliveryInformationFormRequest(DeliveryInformationRequest deliveryInformationRequest,
                                                                 RawEcommerceOrderEntity rawEcommerceOrder) {
        DeliveryInformationEntity deliveryInformationEntity = deliveryInformationRepository.save(
                                                                        DeliveryInformationEntity
                                                                                .builder()
                                                                                .orderNumber(deliveryInformationRequest.getOrderNumber())
                                                                                .orderDate(deliveryInformationRequest.getOrderDate())
                                                                                .recipientName(deliveryInformationRequest.getRecipientName())
                                                                                .deliveryAddress(deliveryInformationRequest.getDeliveryAddress())
                                                                                .phoneNumber(deliveryInformationRequest.getPhoneNumber())
                                                                                .email(deliveryInformationRequest.getEmail())
                                                                                .noteTimeRecipient(deliveryInformationRequest.getNoteTimeRecipient())
                                                                                .rawEcommerceOrder(rawEcommerceOrder)
                                                                                .status(EStatus.DELIVERING)
                                                                                .paymentSt(deliveryInformationRequest.getPaymentSt())
                                                                                .build()
                                                                );
        return modelMapper.map(deliveryInformationEntity, DeliveryInformation.class);
    }

    private final Set<String> districtInnerAreaSet = new HashSet<>(Arrays.asList(
            "Quận Hải Châu", "Quận Cẩm Lệ", "Quận Thanh Khê",
            "Quận Liên Chiểu", "Quận Ngũ Hành Sơn", "Quận Sơn Trà"
    ));
    @Override
    public List<DeliveryInformationByDistrict> groupDeliveryInformationByDistrict() {
        List<DeliveryInformationEntity> deliveryInformationList = deliveryInformationRepository
                .findAllByStatusIs(EStatus.DELIVERING);
        System.out.println("Size groub: "+deliveryInformationList.size());
        if(!deliveryInformationList.isEmpty()){
            Map<String, List<DeliveryInformation>> groupDeliveryInformation = new HashMap<>();

            for (DeliveryInformationEntity deliveryInformationEntity : deliveryInformationList) {
                String district = getDistrict(deliveryInformationEntity.getDeliveryAddress());
                if (districtInnerAreaSet.contains(district)) {
                    /*
                     * Bởi vì groupDeliveryInformation ban đầu có thể là chưa được khởi tạo
                     * hoặc là map rỗng cho nên mới cần dùng .computeIfAbsent
                     * AE xài Map theo kiểu của Java8+ này đỡ phải if-else dài dòng*/
                    groupDeliveryInformation
                            //Nếu district is present in Map return value theo district, rồi add thêm deliveryAddress
                            //Nếu key không tồn tại thì nó sẽ tạo mới một key(là district) và value được add vào.
                            .computeIfAbsent(district, key -> new ArrayList<>())
                            .add(modelMapper.map(deliveryInformationEntity, DeliveryInformation.class));
                }
            }
            return groupDeliveryInformation
                    .entrySet()
                    .stream()
                    .map(entry -> DeliveryInformationByDistrict.builder()
                            .districtName(entry.getKey())
                            .deliveryInformationList(entry.getValue())
                            .build())
                    .collect(toList());
        }
        return new ArrayList<>();
    }

    @Override
    public String getDistrict(String deliveryAddress) {
        deliveryAddress = deliveryAddress.trim();
        String[] splitAddress = deliveryAddress.split(",");
        String district = "";
        if(splitAddress.length >= 3){
            district = splitAddress[2].trim();
        }
        return district;
    }

    @Override
    public ResponseEntity<?> getTransportOrder() {
        List<DeliveryInformationByDistrict> deliveryInformationByDistrictList = groupDeliveryInformationByDistrict();
        try {
            if(!deliveryInformationByDistrictList.isEmpty()){

                TransportOrderResponse<List<DeliveryInformationByDistrict>> transportOrders = new TransportOrderResponse<>();
                transportOrders.setData(deliveryInformationByDistrictList);

                return ResponseEntity
                        .status(HttpStatusCode.valueOf(200))
                        .body(
                                ResponseObject
                                        .builder()
                                        .status("SUCCESS")
                                        .message("Get Transport Order Success")
                                        .results(transportOrders)
                                        .build()
                        );
            }
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(204))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("FAIL")
                                    .build()
                    );

        }catch (Exception e){
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(404))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("FAIL")
                                    .message(e.getMessage())
                                    .results("")
                                    .build()
                    );
        }
    }

    @Override
    public ResponseEntity<?> getTransportOrderByShipper(Long shipperId,
                                                        List<DeliveryInformation> deliveryInformationList) {
        if(!userRepository.existsById(shipperId)){
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(204))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("USER NOT FOUND!")
                                    .build()
                    );
        }

        try{
            if(!deliveryInformationList.isEmpty()){
                UserEntity shipper = userRepository.findById(shipperId)
                        .orElseThrow(NoSuchElementException::new);
                List<DeliveryInformationEntity> deliveryInformationEntities = deliveryInformationList
                        .stream()
                        .map(deliveryInformation -> {
                            deliveryInformation.setShipper(shipper);
                            return modelMapper.map(deliveryInformation, DeliveryInformationEntity.class);
                        })
                        .collect(Collectors.toList());

                List<DeliveryInformation> deliveryInformations = deliveryInformationRepository.saveAll(deliveryInformationEntities)
                        .stream()
                        .map(deliveryInformationEntity -> modelMapper.map(deliveryInformationEntity, DeliveryInformation.class))
                        .toList();

                TransportOrderResponse<List<DeliveryInformation>> shippingInformation = new TransportOrderResponse<>();
                shippingInformation.setData(deliveryInformations);

                return ResponseEntity
                        .status(HttpStatusCode.valueOf(200))
                        .body(
                                ResponseObject
                                        .builder()
                                        .status("SUCCESS")
                                        .message("Shipping Information's Already")
                                        .results(shippingInformation)
                                        .build()
                        );
            }
        }catch (Exception e){
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(404))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("FAIL")
                                    .message(e.getMessage())
                                    .results("")
                                    .build()
                    );
        }
        return null;
    }

    @Override
    public ResponseEntity<?> changeStatusDelivery(Long deliveryInformationId, Boolean currentStatus) {
        try{
            DeliveryInformationEntity deliveryInformationEntity = deliveryInformationRepository.findById(deliveryInformationId)
                    .orElseThrow(NoSuchElementException::new);
            if(currentStatus){
                deliveryInformationEntity.setStatus(EStatus.DELIVERED_SUCCESSFULLY);
                deliveryInformationEntity.setDeliveryDate(LocalDateTime.now());

                String message = "Thank you for your trust and the opportunity for us to serve you.\\n%s\\n" +
                        "Please click on the link below to rate product's quality:";
                emailService.sendEmail(deliveryInformationEntity.getEmail(),"Completed The Order", message);
            }else{
                deliveryInformationEntity.setStatus(EStatus.DELIVERY_FAILED);
                deliveryInformationEntity.setDeliveryDate(LocalDateTime.now());

                String message = "Thanks for your interest in our products.\\n%s\\n" +
                        "To purchase next time, please click on the link:";
                emailService.sendEmail(deliveryInformationEntity.getEmail(),"Fail The Order", message);
            }
            deliveryInformationRepository.save(deliveryInformationEntity);

            return ResponseEntity
                    .status(HttpStatusCode.valueOf(200))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("SUCCESS")
                                    .message("Change Status Success")
                                    .build()
                    );
        }catch (Exception e){
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(404))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("FAIL")
                                    .message(e.getMessage())
                                    .results("")
                                    .build()
                    );
        }
    }

//    @Override
//    public ResponseRouteApi testRoute(){
//        List<DeliveryInformationByDistrict> deliveryInformationByDistrictList = this.groupDeliveryInformationByDistrict();
//        System.out.println("sizee: "+deliveryInformationByDistrictList.size());
//            List<String> deliveryAddressList = deliveryInformationByDistrictList.get(0).getDeliveryInformationList()
//                    .stream()
//                    .map(DeliveryInformation::getDeliveryAddress).toList();
//
//        String placeTsp = "92 Quang Trung, Hải Châu, TP Đà Nẵng";
//        String resulRoute = mapService
//                    .getRouteResolveTSP(placeTsp.replace(" ","+"),
//                            placeTsp.replace(" ","+"),
//                            deliveryAddressList);
//
//            System.out.println("Result: "+resulRoute);
//            System.out.println(deliveryAddressList.size());
//            System.out.println("==========================");
//
//        return resulRoute;
//    }

}
