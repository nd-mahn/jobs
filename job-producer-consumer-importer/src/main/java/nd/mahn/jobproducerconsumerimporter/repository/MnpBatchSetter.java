package nd.mahn.jobproducerconsumerimporter.repository;

import lombok.RequiredArgsConstructor;
import nd.mahn.jobproducerconsumerimporter.dto.MnpDto;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@RequiredArgsConstructor
public class MnpBatchSetter implements BatchPreparedStatementSetter {
    
    private final List<MnpDto> data;

    @Override
    public void setValues(PreparedStatement ps, int i) throws SQLException {
        MnpDto dto = data.get(i);
        // Thứ tự index phải khớp tuyệt đối với câu SQL insert-temp trong application.yml
        ps.setString(1, dto.getStrNetOperatorFrom());
        ps.setString(2, dto.getStrOutNetDate());
        ps.setString(3, dto.getStrNetOperatorToId());
        ps.setString(4, dto.getStrIsdn());
        ps.setString(5, dto.getStrBlockHolder());
        ps.setString(6, dto.getStrInNetDate());
        ps.setString(7, dto.getStatus());
        ps.setString(8, dto.getIndexOrder());
    }

    @Override
    public int getBatchSize() {
        return data == null ? 0 : data.size();
    }
}
