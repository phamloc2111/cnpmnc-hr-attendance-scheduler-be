package db.migration;
import java.text.Normalizer;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;

public class V6__shared_positions extends BaseJavaMigration {
    private record Existing(String table,String field,String idField) {}
    private record CatalogEntry(UUID id,String name) {}
    private static String display(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}]+"," ").strip();
    }
    @Override
    public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        try(var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE organization_positions (id UUID PRIMARY KEY,name VARCHAR(120) NOT NULL,normalized_name VARCHAR(240) NOT NULL UNIQUE,active BOOLEAN NOT NULL,created_by UUID,version BIGINT NOT NULL DEFAULT 0)");
            statement.execute("ALTER TABLE identity_accounts ADD COLUMN position_id UUID REFERENCES organization_positions(id)");
            statement.execute("ALTER TABLE work_shift_templates ADD COLUMN required_position_id UUID REFERENCES organization_positions(id)");
        }
        var catalog=new HashMap<String,CatalogEntry>();
        for(var source:List.of(new Existing("identity_accounts","position","position_id"),
                               new Existing("work_shift_templates","required_position","required_position_id"))) {
            var values=new ArrayList<Map.Entry<UUID,String>>();
            try(var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT id,"+source.field()+" FROM "+source.table()+" WHERE "+source.field()+" IS NOT NULL ORDER BY "+source.field()+",id")) {
                while(rows.next()) values.add(Map.entry(rows.getObject("id",UUID.class),rows.getString(source.field())));
            }
            for(var value:values) {
                var name=display(value.getValue());
                if(name.isBlank()) continue;
                var key=name.toLowerCase(Locale.ROOT);
                var position=catalog.get(key);
                if(position==null) {
                    position=new CatalogEntry(UUID.randomUUID(),name);
                    try(var insert=connection.prepareStatement("INSERT INTO organization_positions(id,name,normalized_name,active,version) VALUES(?,?,?,true,0)")) {
                        insert.setObject(1,position.id());insert.setString(2,name);insert.setString(3,key);insert.executeUpdate();
                    }
                    catalog.put(key,position);
                }
                try(var update=connection.prepareStatement("UPDATE "+source.table()+" SET "+source.idField()+"=? WHERE id=?")) {
                    update.setObject(1,position.id());update.setObject(2,value.getKey());update.executeUpdate();
                }
            }
        }
        try(var statement=connection.createStatement()) {
            statement.execute("CREATE INDEX idx_account_position ON identity_accounts(position_id)");
            statement.execute("CREATE INDEX idx_shift_position ON work_shift_templates(required_position_id)");
        }
    }
}
