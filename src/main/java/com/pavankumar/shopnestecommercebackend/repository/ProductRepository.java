package com.pavankumar.shopnestecommercebackend.repository;

import com.pavankumar.shopnestecommercebackend.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;


public interface ProductRepository extends JpaRepository<Product,Long> {


    @Query("select p from Product p join fetch p.category ")
    List<Product> findAllWithCategory();

    @Query("select p from Product p join fetch p.category where p.id=:id")
    Optional<Product> findByProductId(@Param("id") Long id);

    boolean existsByCategory_Id(Long categoryId);

    @Query("select p from Product p join fetch p.category where p.category.id=:id")
    List<Product> findByCategoryIdWithCategory(@Param("id") Long categoryId);

    @Query("select p from Product p join fetch p.category where lower(p.name) "+
            "like lower(concat('%',:keyword,'%')) ")
    List<Product> findByNameContainingIgnoreCaseWithCategory(String keyword);


    @Query(value = "select p from Product p join fetch p.category ",
    countQuery = "select count(p) from Product p")
    Page<Product> findAllWithCategoryPaginated(Pageable pageable);

    @Query(value = "select p from Product p join fetch p.category where lower(p.name) "+
    "like lower(concat('%',:keyword,'%')) ",
    countQuery = "select count(p) from Product p where lower(p.name) like lower(concat('%',:keyword,'%') ) ")
    Page<Product> findByNameContainingIgnoreCasePaginated(@Param("keyword") String keyword,Pageable pageable);

    @Query(value = "select p from Product p join fetch p.category where p.category.id=:id",
    countQuery = "select count(p) from Product p where p.category.id=:id")
    Page<Product> findByCategory_IdPaginated(@Param("id") Long categoryId, Pageable pageable);


}
