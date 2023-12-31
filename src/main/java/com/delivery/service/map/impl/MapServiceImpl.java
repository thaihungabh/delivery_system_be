package com.delivery.service.map.impl;

import com.delivery.DTO.DataResponse;
import com.delivery.DTO.route.ResponseGetRoute;
import com.delivery.DTO.route.WaypointMarker;
import com.delivery.model.geocoding.ResponseApi;
import com.delivery.service.map.IMapService;
import com.delivery.util.ResponseObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.shaded.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class MapServiceImpl implements IMapService {
    @Value("${map4d.api-key}")
    private String apiKey;
    private final RestTemplate restTemplate;

    public MapServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ResponseApi getLocationByAddress(String address) {
        String geocodeApiUrl = "https://api.map4d.vn/sdk/v2/geocode?key="+apiKey+"&address="+address;
        return restTemplate.getForObject(geocodeApiUrl, ResponseApi.class);
    }

    @Override
    public Boolean checkExistAddress(String address) {
        return getLocationByAddress(address).getCode().equals("ok");
    }

    @Override
    public String getRouteResolveTSP(String original, String destination, List<String> deliveryAddressList) {
        StringBuilder points = new StringBuilder();
        for(String s : deliveryAddressList){
            s = s.trim();
            points.append(s.replace(" ", "%").concat(";"));
        }
//        System.out.println(points);

        String routeApiUrl = "http://api.map4d.vn/sdk/route?key="+apiKey+"&origin="+original+
                "&destination="+destination+"&points="+points+"&mode=motorcycle"+"&optimize=True";
//        System.out.println(routeApiUrl);

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(routeApiUrl, String.class);
        String responseBody = responseEntity.getBody();
        return responseBody;
    }

    @Override
    public ResponseEntity<?> getDirectionAndPositionWayPointer(String original,
                                                               String destination,
                                                               List<String> deliveryAddressList) {

       String  resultDirection = this.getRouteResolveTSP(original,destination,deliveryAddressList);

        try {
            List<WaypointMarker> waypointMarkers = new ArrayList<>();
            //Get Location's WayPointer-address
            for(String deliveryAddress : deliveryAddressList){
                //Call api get lat-long location
                ResponseApi responseGeocoding = this.getLocationByAddress(deliveryAddress);

                String[] splitAddress = deliveryAddress.split(",");
                String address = splitAddress[0].trim();

                waypointMarkers.add(WaypointMarker.builder()
                        .position(responseGeocoding.getResult().get(0).getLocation())
                        .title(address)
//                        .draggable(true)
//                        .visible(true)
                        .build());
            }
//            ResponseGetRoute responseGetRoute = ResponseGetRoute
//                    .builder()
//                    .resultDirection(resultDirection)
//                    .waypointMarker(waypointMarkers)
//                    .build();

            DataResponse<Object,List<WaypointMarker>> dataResponse = new DataResponse<>();
            dataResponse.setData(resultDirection);
            dataResponse.setData2(waypointMarkers);

            return ResponseEntity
                    .status(HttpStatusCode.valueOf(200))
                    .body(
                            ResponseObject
                                    .builder()
                                    .status("SUCCESS")
                                    .message("Get Direction Success")
                                    .results(dataResponse)
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
}
