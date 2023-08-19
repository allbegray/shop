create table user
(
    id         bigint auto_increment primary key,
    name       varchar(32)            not null,
    created_at datetime default now() not null,
    updated_at datetime default now() not null,
    deleted_at datetime
);