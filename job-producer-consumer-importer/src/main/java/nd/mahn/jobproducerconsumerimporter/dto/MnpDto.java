package nd.mahn.jobproducerconsumerimporter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MnpDto {
    // Column 0: Mã nhà mạng chuyển đi (1:mobifone; 2: vinaphone; 4: Viettel; 5: Vietnam Mobile; 7: Gtel; 8: Đông Dương)
    private String strNetOperatorFrom;
    // Column 1: Ngày TB chuyển đi (Ngày rời khỏi nhà mạng chuyển đến)
    private String strOutNetDate;
    // Column 2: Mã nhà mạng chuyển đến (1:mobifone; 2: vinaphone; 4: Viettel; 5: Vietnam Mobile; 7: Gtel; 8: Đông Dương)
    private String strNetOperatorToId;
    // Column 3: Số TB
    private String strIsdn;
    // Column 4: Mã nhà mạng gốc (1:mobifone; 2: vinaphone; 4: Viettel; 5: Vietnam Mobile; 7: Gtel; 8: Đông Dương)
    private String strBlockHolder;
    // Column 5: Ngày Tb chuyển đến (Ngày chuyển đến nhà mạng mới)
    private String strInNetDate;
    // Column 6: Status (1: đang tham gia mnp, 0: không còn tham gia mnp, trả về mạng gốc)
    private String status;
    // Column 7: Số thứ tự row trong file
    private String indexOrder;
}
