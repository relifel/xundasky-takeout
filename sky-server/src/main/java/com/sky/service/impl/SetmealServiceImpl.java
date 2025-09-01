package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 套餐业务实现
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐，同时需要保存套餐和菜品的关联关系
     * @param setmealDTO
     */
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id
        Long setmealId = setmeal.getId();

        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        //保存套餐和菜品的关联关系
        setmealDishMapper.insertBatch(setmealDishes);
    }
    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        int pageNum = setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }
    /**
     * 批量删除套餐
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        ids.forEach(id -> {
            Setmeal setmeal = setmealMapper.getById(id);
            if(StatusConstant.ENABLE == setmeal.getStatus()){
                //起售中的套餐不能删除
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        });

        ids.forEach(setmealId -> {
            //删除套餐表中的数据
            setmealMapper.deleteById(setmealId);
            //删除套餐菜品关系表中的数据
            setmealDishMapper.deleteBySetmealId(setmealId);
        });
    }
    /**
     * 根据id查询套餐和套餐菜品关系
     *
     * @param id
     * @return
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        //根据id查询套餐表数据
        Setmeal setmeal = setmealMapper.getById(id);//删除菜品时写过了
        //根据id查询餐菜品关系表数据
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);

        //封装返回结果
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);

        return setmealVO;
    }


    /**
     * 修改套餐
     *
     * 思路分析：套餐表修改直接使用update语句即可，对于这个套餐菜品关系表，
     *         套餐和菜品关系的修改比较复杂，因为它的情况有很多 有可能关系没有修改 有可能
     *         关系是追加的 也有可能关系是删除了，那么这个地方我们有没有一种比较
     *         简单的处理方式呢？？？
     *         可以先把你当前这个套餐菜品关系数据全都统一删掉，然后在按照你当前
     *         传过来的这个套餐菜品关系，重新再来插入一遍这个数据就可以了。
     *
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        //说明：SetmealDTO含有套餐菜品关系表数据，当前只是修改套餐的基本信息，所以直接传递SetmealDTO不合适，
        //     可以把SetmealDTO的数据拷贝到套餐的基本信息类Setmeal中更合适。
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //1、修改套餐表，执行update
        setmealMapper.update(setmeal);

        //获取生成的套餐id   通过sql中的useGeneratedKeys="true" keyProperty="id"获取插入后生成的主键值
        //套餐菜品关系表的setmealId页面不能传递，它是向套餐表插入数据之后生成的主键值，也就是套餐菜品关系表的逻辑外键setmealId
        Long setmealId = setmealDTO.getId();//新增套餐时的sql获取主键值

        //2、删除套餐和菜品的关联关系，操作setmeal_dish表，执行delete
        setmealDishMapper.deleteBySetmealId(setmealId); //删除套餐时已经实现了

        //获取页面传来的套餐和菜品关系表数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        //遍历关系表数据，为关系表中的每一条数据(每一个对象)的setmealId赋值，
        //   这个地方不需要像之前写新增菜品时多写个if判断，因为之前的口味数据是非必须的，
        //   这个地方要求套餐必须包含菜品是必须的，所以不需要if判断，不存在套餐不包含菜品得情况
        setmealDishes.forEach(setmealDish -> {
            setmealDish.setSetmealId(setmealId);
        });

        //3、重新插入套餐和菜品的关联关系，操作setmeal_dish表，执行insert
        //   动态sql批量插入
        setmealDishMapper.insertBatch(setmealDishes);//新增套餐时已经实现了
    }

    /**
     * 套餐起售、停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未启售菜品，无法启售"
        if(status.equals(StatusConstant.ENABLE)){  //1  启用
            //select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = ?
            //左外连接查询，根据套餐id查询菜品以及对应的菜品套餐关系数据，a.*所以返回所有菜品数据
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if(dishList != null && dishList.size() > 0){//判断套餐中是否包含的有菜品，有才走if判断
                dishList.forEach(dish -> {
                    //套餐中包含菜品，如果这个菜品的状态为禁用，则抛出异常
                    if(StatusConstant.DISABLE.equals(dish.getStatus())){
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }


        //执行流程： 如果是起售套餐，套餐内有停售菜品，则抛出异常 不能起售
        //         如果是起售套餐，套餐内没有停售菜品，if执行完后跳出继续向下执行，执行更新
        //         如果是停售套餐，不走上面的if，直接进行更新状态。
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);//修改套餐时写了通用的修改sql
    }


}
