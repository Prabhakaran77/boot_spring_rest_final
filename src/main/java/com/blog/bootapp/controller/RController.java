package com.blog.bootapp.controller;


import com.blog.bootapp.BootappApplication;
import com.blog.bootapp.MyUserDetailService;
import com.blog.bootapp.model.*;
import com.blog.bootapp.service.CategoryService;
import com.blog.bootapp.service.PostService;
import com.blog.bootapp.service.UserService;
import com.blog.bootapp.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/rest/posts")
public class  RController
{
    @Autowired
    private PostService ps;
    @Autowired
    private CategoryService cs;
    @Autowired
    private UserService us;

    private static final Logger LOGGER= LoggerFactory.getLogger(BootappApplication.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    MyUserDetailService myUserDetailService;

    @Autowired
    JwtUtil jwtTokenUtil;

    String currentUserName;
    long author_id;
    String author;
    String ADMIN="Admin";
    private PageRequest gotoPage(int page,int size)
    {
        PageRequest request = PageRequest.of(page,size);
        return request;
    }

    int totalPostsCount;
    @RequestMapping(value = "/", method= RequestMethod.GET)
    public ResponseEntity firstRequest(@RequestParam(value="searchBy", defaultValue = "") String searchBy,
                                   @RequestParam(value="pageNo", required=false, defaultValue="0") String pageNo,
                                   @RequestParam(value="size", required=false, defaultValue = "5") int size,
                                   @RequestParam(value="sort-by", defaultValue = "") String sortKeyWord,
                                   @RequestParam(value="filter-by-auth", defaultValue ="-1" ) int authId,
                                   @RequestParam(value="filter-by-cat", defaultValue = "-1") int categId)
    {
        int gotoPageNo=Integer.parseInt(pageNo);
        List<Long> postIds=ps.allFilters(searchBy,authId,categId);
        totalPostsCount=postIds.size();
        List<Post> postList=null;
        if(totalPostsCount>0)
        {
            postList = ps.findByIds(postIds, sortKeyWord, gotoPage(gotoPageNo,size)).getContent();
            return new ResponseEntity(postList, HttpStatus.OK);
        }
        else
        {
            return new ResponseEntity("No Post Found", HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/{id}", method= RequestMethod.GET)
    public Post singlePost(@PathVariable Long id)
    {
        return ps.getPostById(id);
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public ResponseEntity saveProduct(@RequestParam Long categoryId,
                                      @RequestParam String title,
                                      @RequestParam String content)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        currentUserName = authentication.getName();
        List<User> userList = us.listAll();
        author_id=ps.authorId(userList,currentUserName);
        Post post=new Post();
        post.setAuthorId(author_id);
        List<Category> catList=new ArrayList<>();
        catList.add(cs.get(categoryId));
        post.setCategoryList(catList);
        post.setContent(content);
        post.setTitle(title);
        ps.save(post);
        return new ResponseEntity("Post saved successfully", HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public ResponseEntity updateProduct(@PathVariable Long id, @RequestBody Post post,
                                        @RequestParam String title,
                                        @RequestParam String content)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        currentUserName = authentication.getName();
        List<User> userList = us.listAll();
        author_id=ps.authorId(userList,currentUserName);
        if(!ps.isPostExist(id))
        {
            LOGGER.warn(currentUserName+" tried to update a post which doesn't exist");
            return new ResponseEntity("post doesn't exist",HttpStatus.BAD_REQUEST);
        }
        Post storedPost = ps.getPostById(id);
        if(storedPost.getAuthorId()==author_id||currentUserName.equals(ADMIN)) {
            storedPost.setAuthorId(post.getAuthorId());
            storedPost.setCategoryList(post.getCategoryList());
            if (content.equals("")) {
                storedPost.setContent(post.getContent());
            } else {
                storedPost.setContent(content);
            }
            if (title.equals("")) {
                storedPost.setTitle(post.getTitle());
            } else {
                storedPost.setTitle(title);
            }
            ps.save(storedPost);
            return new ResponseEntity("Post updated successfully", HttpStatus.OK);
        }
        else
        {
            author=ps.authorName(userList,storedPost.getAuthorId());
            LOGGER.info("Author: "+currentUserName+" tried to edit "+author+"'s post'");
            return new ResponseEntity("you aren't authorized to update this post",HttpStatus.UNAUTHORIZED);
        }
    }

    @RequestMapping(value="/{id}", method = RequestMethod.DELETE)
    public ResponseEntity delete(@PathVariable Long id)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        currentUserName = authentication.getName();
        List<User> userList = us.listAll();
        author_id=ps.authorId(userList,currentUserName);
        if(!ps.isPostExist(id))
        {
            LOGGER.warn(currentUserName+" tried to delete a post which doesn't exist");
            return new ResponseEntity("post doesn't exist",HttpStatus.BAD_REQUEST);
        }
        Post storedPost = ps.getPostById(id);
        if(storedPost.getAuthorId()==author_id||currentUserName.equals("Admin")) {
            ps.delete(id);
            return new ResponseEntity("Post deleted successfully", HttpStatus.OK);
        }
        else
        {
            author=ps.authorName(userList,storedPost.getAuthorId());
            LOGGER.info("Author: "+currentUserName+" tried to delete "+author+"'s post'");
            return new ResponseEntity("you aren't authorized to delete this post",HttpStatus.UNAUTHORIZED);
        }
    }

    @RequestMapping(value = "/authenticate",method = RequestMethod.POST)
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthenticationRequest authenticationRequest) throws Exception
    {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authenticationRequest.getUsername(), authenticationRequest.getPassword())
            );
        }
        catch (BadCredentialsException e)
        {
            throw new Exception("incorrect username or password",e);
        }
        final UserDetails userdetails= myUserDetailService.loadUserByUsername(authenticationRequest.getUsername());

        final  String jwt=jwtTokenUtil.generateToken(userdetails);
        return ResponseEntity.ok(new AuthenticationResponse(jwt));
    }
}
