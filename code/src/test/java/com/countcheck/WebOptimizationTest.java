package com.countcheck;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_JDBC_URL",matches=".+")
class WebOptimizationTest {
  @Autowired Store store;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  Store.Member member;
  final String password="disposable-web-test-password";

  @BeforeEach void setup(){
    String email=UUID.randomUUID()+"@example.invalid";
    store.register("Web QA","Catalog testing",email,password);member=store.member(email);
  }
  @AfterEach void cleanup(){store.deleteAccount(member,password);}

  @Test void catalogPagesStayBoundedAndSearchFindsLaterProducts()throws Exception {
    jdbc.update("INSERT INTO products(business_id,name,sku,unit,location,recorded_count) "
        + "SELECT ?, 'Widget '||lpad(n::text,3,'0'), 'SKU-'||n, 'pieces','Shelf',0 FROM generate_series(1,121) n",member.businessId());
    String html=mvc.perform(get("/products").with(user(member.email())))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(html.contains("121 products"));assertTrue(html.contains("SKU-50"));assertFalse(html.contains("SKU-51<"));
    assertTrue(html.contains("Next page"));
    String search=mvc.perform(get("/counts/new").param("q","SKU-121").with(user(member.email())))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(search.contains("Widget 121"));assertFalse(search.contains("Widget 001"));
  }

  @Test void exportIncludesAllRowsAcrossDatabaseBatches()throws Exception {
    store.addProduct(member.businessId(),"CSV item","CSV","pieces","Shelf","1");
    long product=store.products(member.businessId()).getFirst().id();
    jdbc.update("INSERT INTO count_entries(business_id,product_id,member_id,request_id,item_name,sku,location,recorded_count,physical_count,note) "
        + "SELECT ?,?,?,gen_random_uuid(),'CSV item','CSV','Shelf',1,2,'=SUM(1)' FROM generate_series(1,601)",member.businessId(),product,member.id());
    var result=mvc.perform(get("/counts/export.csv").with(user(member.email())))
        .andExpect(request().asyncStarted()).andReturn();
    result.getAsyncResult(30000);
    String csv=mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertEquals(602,csv.lines().count());assertTrue(csv.contains("'=SUM(1)"));
    assertEquals(601,store.entryCount(member.businessId()));
  }
}
